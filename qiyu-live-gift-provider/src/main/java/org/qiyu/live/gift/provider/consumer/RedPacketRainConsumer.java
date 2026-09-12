package org.qiyu.live.gift.provider.consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.idea.qiyu.live.framework.redis.starter.key.GiftProviderCacheKeyBuilder;
import org.qiyu.live.common.interfaces.dto.RedPacketMqDTO;
import org.qiyu.live.common.interfaces.topic.GiftProviderTopicNames;
import org.qiyu.live.framework.mq.starter.properties.RocketMQConsumerProperties;
import org.qiyu.live.gift.provider.service.IRedPacketService;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 红包雨MQ消费者
 * 处理红包雨发送通知和用户领取通知
 */
@Configuration
public class RedPacketRainConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedPacketRainConsumer.class);

    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @Resource
    private IRedPacketService redPacketService;
    @DubboReference(check = false)
    private ImRouterRpc routerRpc;
    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private GiftProviderCacheKeyBuilder cacheKeyBuilder;

    @Override
    public void afterPropertiesSet() throws Exception {
        // 消费者1：处理红包雨发送通知
        DefaultMQPushConsumer sendConsumer = new DefaultMQPushConsumer();
        sendConsumer.setVipChannelEnabled(false);
        sendConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        sendConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_RedPacketSend");
        sendConsumer.setConsumeMessageBatchMaxSize(1);
        sendConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        sendConsumer.subscribe(GiftProviderTopicNames.RED_PACKET_RAIN_SEND, "");
        sendConsumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    RedPacketMqDTO mqDTO = JSON.parseObject(new String(msg.getBody()), RedPacketMqDTO.class);
                    LOGGER.info("[RedPacketRainConsumer] 收到红包雨发送通知, redPacketId={}", mqDTO.getRedPacketId());

                    // 通过IM推送给直播间所有用户
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("redPacketId", mqDTO.getRedPacketId());
                    jsonObject.put("configCode", mqDTO.getConfigCode());
                    jsonObject.put("totalCount", mqDTO.getTotalCount());

                    LivingRoomReqDTO reqDTO = new LivingRoomReqDTO();
                    reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
                    reqDTO.setRoomId(mqDTO.getRoomId());
                    List<Long> userIdList = livingRoomRpc.queryUserIdByRoomId(reqDTO);

                    // 批量发送红包雨通知给所有用户
                    batchSendImMsg(userIdList, ImMsgBizCodeEnum.RED_PACKET_RAIN_SEND, jsonObject);
                    LOGGER.info("[RedPacketRainConsumer] 红包雨通知已推送, roomId={}, userCount={}", mqDTO.getRoomId(), userIdList.size());
                } catch (Exception e) {
                    LOGGER.error("[RedPacketRainConsumer] 处理红包雨通知失败", e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        sendConsumer.start();

        // 消费者2：处理红包领取通知
        DefaultMQPushConsumer receiveConsumer = new DefaultMQPushConsumer();
        receiveConsumer.setVipChannelEnabled(false);
        receiveConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        receiveConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_RedPacketReceive");
        receiveConsumer.setConsumeMessageBatchMaxSize(10);
        receiveConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        receiveConsumer.subscribe(GiftProviderTopicNames.RED_PACKET_RAIN_RECEIVE, "");
        receiveConsumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    RedPacketMqDTO mqDTO = JSON.parseObject(new String(msg.getBody()), RedPacketMqDTO.class);
                    LOGGER.info("[RedPacketRainConsumer] 收到红包领取通知, userId={}, redPacketId={}", mqDTO.getUserId(), mqDTO.getRedPacketId());

                    // 领取成功，推送领取结果给用户
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("redPacketId", mqDTO.getRedPacketId());
                    jsonObject.put("configCode", mqDTO.getConfigCode());

                    // 领取统计异步同步到DB（total_get/total_get_price），供结算使用
                    redPacketService.syncReceiveStat(mqDTO.getRedPacketId(), mqDTO.getReceivePrice());

                    // 单独发送给领取用户
                    sendImMsgSingleton(mqDTO.getUserId(), ImMsgBizCodeEnum.RED_PACKET_RECEIVE_SUCCESS, jsonObject);
                } catch (Exception e) {
                    LOGGER.error("[RedPacketRainConsumer] 处理红包领取通知失败", e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        receiveConsumer.start();

        // 消费者3：红包结算（发送时投递的延迟1分钟消息），退还未领完金额给主播
        DefaultMQPushConsumer settleConsumer = new DefaultMQPushConsumer();
        settleConsumer.setVipChannelEnabled(false);
        settleConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        settleConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_RedPacketSettle");
        settleConsumer.setConsumeMessageBatchMaxSize(1);
        settleConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        settleConsumer.subscribe(GiftProviderTopicNames.RED_PACKET_RAIN_SETTLE, "");
        settleConsumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    RedPacketMqDTO mqDTO = JSON.parseObject(new String(msg.getBody()), RedPacketMqDTO.class);
                    LOGGER.info("[RedPacketRainConsumer] 红包结算触发, redPacketId={}", mqDTO.getRedPacketId());
                    redPacketService.settle(mqDTO.getRedPacketId());
                } catch (Exception e) {
                    LOGGER.error("[RedPacketRainConsumer] 红包结算失败", e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        settleConsumer.start();

        LOGGER.info("[RedPacketRainConsumer] 红包雨MQ消费者启动成功");
    }

    /**
     * 单独发送IM消息
     */
    private void sendImMsgSingleton(Long userId, ImMsgBizCodeEnum bizCodeEnum, JSONObject jsonObject) {
        ImMsgBody imMsgBody = new ImMsgBody();
        imMsgBody.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        imMsgBody.setBizCode(bizCodeEnum.getCode());
        imMsgBody.setUserId(userId);
        imMsgBody.setData(jsonObject.toJSONString());
        routerRpc.sendMsg(imMsgBody);
    }

    /**
     * 批量发送IM消息
     */
    private void batchSendImMsg(List<Long> userIdList, ImMsgBizCodeEnum bizCodeEnum, JSONObject jsonObject) {
        if (userIdList == null || userIdList.isEmpty()) {
            return;
        }
        List<ImMsgBody> imMsgBodies = userIdList.stream().map(userId -> {
            ImMsgBody imMsgBody = new ImMsgBody();
            imMsgBody.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            imMsgBody.setBizCode(bizCodeEnum.getCode());
            imMsgBody.setUserId(userId);
            imMsgBody.setData(jsonObject.toJSONString());
            return imMsgBody;
        }).toList();
        routerRpc.batchSendMsg(imMsgBodies);
    }
}
