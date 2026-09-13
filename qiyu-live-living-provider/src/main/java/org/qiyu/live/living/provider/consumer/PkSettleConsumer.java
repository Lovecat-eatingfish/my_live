package org.qiyu.live.living.provider.consumer;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.qiyu.live.framework.mq.starter.properties.RocketMQConsumerProperties;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.qiyu.live.living.provider.dao.po.LivingRoomPO;
import org.qiyu.live.living.provider.service.ILivingRoomService;
import org.qiyu.live.living.provider.dao.mapper.LivingRoomMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PK 倒计时结算消费者：onlinePk 时发 10 分钟延迟消息，到点判定胜负并广播 5573。
 * pkNum &gt; 50 主播胜 / &lt; 50 连麦方胜 / = 50 平局；结算后置 isOver + 清进度 + 下线 PK。
 */
@Component
public class PkSettleConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(PkSettleConsumer.class);

    public static final String PK_SETTLE_TOPIC = "LivingPkSettleTopic";

    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private LivingRoomMapper livingRoomMapper;
    @Resource
    private org.idea.qiyu.live.framework.redis.starter.key.GiftProviderCacheKeyBuilder giftCacheKeyBuilder;
    @Resource
    private org.idea.qiyu.live.framework.redis.starter.key.LivingProviderCacheKeyBuilder livingCacheKeyBuilder;
    @DubboReference(check = false)
    private ImRouterRpc imRouterRpc;

    @Override
    public void afterPropertiesSet() throws Exception {
        DefaultMQPushConsumer mqPushConsumer = new DefaultMQPushConsumer();
        mqPushConsumer.setVipChannelEnabled(false);
        mqPushConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        mqPushConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_" + this.getClass().getSimpleName());
        mqPushConsumer.setConsumeMessageBatchMaxSize(1);
        mqPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        mqPushConsumer.subscribe(PK_SETTLE_TOPIC, "");
        mqPushConsumer.setMessageListener((MessageListenerConcurrently) (List<MessageExt> msgs, ConsumeConcurrentlyContext context) -> {
            for (MessageExt msg : msgs) {
                try {
                    int roomId = Integer.parseInt(new String(msg.getBody()));
                    settle(roomId);
                } catch (Exception e) {
                    LOGGER.error("[consume] pk settle error, msg={}", new String(msg.getBody()), e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
        LOGGER.info("PK结算消费者启动成功,namesrv is {}", rocketMQConsumerProperties.getNameSrv());
    }

    private void settle(Integer roomId) {
        // key 与 gift/living 写入侧完全同源（builder 全局加载，前缀一致）
        String pkNumKey = org.qiyu.live.common.interfaces.constants.PkConstants.PK_NUM_KEY_PREFIX + roomId;
        String isOverKey = org.qiyu.live.common.interfaces.constants.PkConstants.PK_IS_OVER_KEY_PREFIX + roomId;
        String onlinePkKey = org.qiyu.live.common.interfaces.constants.PkConstants.ONLINE_PK_KEY_PREFIX + roomId;

        Object pkNumObj = redisTemplate.opsForValue().get(pkNumKey);
        if (pkNumObj == null) {
            LOGGER.info("[settle] pk not exist or already settled, roomId={}", roomId);
            return;
        }
        // 已被提前打满结算（送礼置 isOver）则跳过
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(isOverKey))) {
            LOGGER.info("[settle] already over by full-score, roomId={}", roomId);
            redisTemplate.delete(pkNumKey);
            redisTemplate.delete(onlinePkKey);
            return;
        }
        // 房间与 PK 对手
        LivingRoomPO room = livingRoomMapper.selectById(roomId);
        Object pkObjObj = redisTemplate.opsForValue().get(onlinePkKey);
        if (room == null || pkObjObj == null) {
            LOGGER.info("[settle] room or pkObj gone, roomId={}", roomId);
            return;
        }
        long pkNum = Long.parseLong(String.valueOf(pkNumObj));
        Long anchorId = room.getAnchorId();
        Long pkObjId = Long.parseLong(String.valueOf(pkObjObj));

        Long winnerId;
        Long loserId;
        if (pkNum > 50) {
            winnerId = anchorId;
            loserId = pkObjId;
        } else if (pkNum < 50) {
            winnerId = pkObjId;
            loserId = anchorId;
        } else {
            winnerId = null;
            loserId = null;
        }

        JSONObject data = new JSONObject();
        data.put("roomId", roomId);
        data.put("anchorId", anchorId);
        data.put("pkObjId", pkObjId);
        data.put("pkNum", pkNum);
        data.put("winnerId", winnerId);
        data.put("loserId", loserId);
        // 5573 广播全房间（按当前在线用户集合）
        List<Long> userIds = null;
        try {
            org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO reqDTO = new org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO();
            reqDTO.setRoomId(roomId);
            reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            userIds = queryRoomUserIds(roomId);
        } catch (Exception e) {
            LOGGER.error("[settle] query room users error, roomId={}", roomId, e);
        }
        if (userIds != null && !userIds.isEmpty()) {
            for (Long userId : userIds) {
                ImMsgBody body = new ImMsgBody();
                body.setUserId(userId);
                body.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
                body.setBizCode(5573);
                body.setData(data.toJSONString());
                imRouterRpc.batchSendMsg(Collections.singletonList(body));
            }
        }
        // 收尾：置结束标记（送礼加分通道据此停手）+ 清进度 + 下线 PK（TTL 兜底防 key 泄漏）
        stringRedisTemplate.opsForValue().set(isOverKey, "1", 2, TimeUnit.HOURS);
        redisTemplate.delete(pkNumKey);
        redisTemplate.delete(onlinePkKey);
        LOGGER.info("[settle] roomId={} pkNum={} winnerId={}", roomId, pkNum, winnerId);
    }

    private List<Long> queryRoomUserIds(Integer roomId) {
        // 房间用户集合（与 LivingRoomServiceImpl 同 key）
        String setKey = org.qiyu.live.common.interfaces.constants.PkConstants.ROOM_USER_SET_PREFIX + roomId;
        java.util.Set<Object> members = redisTemplate.opsForSet().members(setKey);
        if (members == null) {
            return Collections.emptyList();
        }
        return members.stream().map(o -> Long.valueOf(String.valueOf(o))).collect(java.util.stream.Collectors.toList());
    }
}
