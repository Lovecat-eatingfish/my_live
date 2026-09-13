package org.qiyu.live.living.provider.consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.qiyu.live.common.interfaces.constants.LotteryConstants;
import org.qiyu.live.framework.mq.starter.properties.RocketMQConsumerProperties;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 口令抽奖结算消费者：createLottery 发对应时长档位的延迟消息，到点随机抽取中奖者，
 * 广播 5575 中奖名单；奖励金币按中奖人数均分（不能整除的余数退回主播）；
 * 无人参与时全额退回主播。结算后清理 Redis 上下文与参与者集合。
 */
@Component
public class LotterySettleConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(LotterySettleConsumer.class);

    public static final String LOTTERY_SETTLE_TOPIC = "LivingLotterySettleTopic";

    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    @DubboReference(check = false)
    private ImRouterRpc imRouterRpc;
    @DubboReference(check = false)
    private org.qiyu.live.bank.interfaces.IQiyuCurrencyAccountRpc currencyAccountRpc;

    @Override
    public void afterPropertiesSet() throws Exception {
        DefaultMQPushConsumer mqPushConsumer = new DefaultMQPushConsumer();
        mqPushConsumer.setVipChannelEnabled(false);
        mqPushConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        mqPushConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_" + this.getClass().getSimpleName());
        mqPushConsumer.subscribe(LOTTERY_SETTLE_TOPIC, "");
        mqPushConsumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    // 发送侧 body 是裸 roomId 字符串（与 PkSettleConsumer 同构）
                    JSONObject body = new JSONObject();
                    body.put("roomId", Integer.parseInt(new String(msg.getBody()).trim()));
                    settle(body);
                } catch (Exception e) {
                    LOGGER.error("[settle] error, msg={}", new String(msg.getBody()), e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
        LOGGER.info("lottery settle consumer started");
    }

    private void settle(JSONObject body) {
        Integer roomId = body.getInteger("roomId");
        if (roomId == null) {
            return;
        }
        String ctxKey = LotteryConstants.ROOM_LOTTERY_KEY_PREFIX + roomId;
        String participantsKey = LotteryConstants.ROOM_LOTTERY_PARTICIPANTS_PREFIX + roomId;
        String ctxStr = stringRedisTemplate.opsForValue().get(ctxKey);
        if (ctxStr == null) {
            LOGGER.info("[settle] lottery ctx expired, roomId={}", roomId);
            return;
        }
        JSONObject ctx = JSON.parseObject(ctxStr);
        stringRedisTemplate.delete(ctxKey);

        Long anchorId = ctx.getLong("anchorId");
        int winnerCount = ctx.getIntValue("winnerCount");
        int rewardCoins = ctx.getIntValue("rewardCoins");
        String keyword = ctx.getString("keyword");

        java.util.Set<String> participants = stringRedisTemplate.opsForSet().members(participantsKey);
        stringRedisTemplate.delete(participantsKey);
        List<Long> participantIds = new ArrayList<>();
        if (participants != null) {
            participants.forEach(o -> participantIds.add(Long.valueOf(o)));
        }

        JSONObject result = new JSONObject();
        result.put("roomId", roomId);
        result.put("keyword", keyword);
        result.put("participantCount", participantIds.size());

        if (participantIds.isEmpty()) {
            if (rewardCoins > 0) {
                currencyAccountRpc.incr(anchorId, rewardCoins);
            }
            result.put("winners", Collections.emptyList());
            result.put("rewardPerPerson", 0);
            LOGGER.info("[settle] no participants, refund {} to anchor {}, roomId={}", rewardCoins, anchorId, roomId);
        } else {
            Collections.shuffle(participantIds);
            int actualWinners = Math.min(winnerCount, participantIds.size());
            List<Long> winners = new ArrayList<>(participantIds.subList(0, actualWinners));
            int rewardPerPerson = rewardCoins / actualWinners;
            int remainder = rewardCoins - rewardPerPerson * actualWinners;
            if (remainder > 0) {
                currencyAccountRpc.incr(anchorId, remainder);
            }
            for (Long winnerId : winners) {
                if (rewardPerPerson > 0) {
                    currencyAccountRpc.incr(winnerId, rewardPerPerson);
                }
            }
            result.put("winners", winners);
            result.put("rewardPerPerson", rewardPerPerson);
            LOGGER.info("[settle] roomId={} winners={} rewardPer={}", roomId, winners, rewardPerPerson);
        }

        // 广播 5575 开奖结果
        try {
            List<ImMsgBody> bodies = new ArrayList<>();
            participantIds.add(anchorId);
            for (Long uid : participantIds) {
                ImMsgBody msgBody = new ImMsgBody();
                msgBody.setUserId(uid);
                msgBody.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
                msgBody.setBizCode(ImMsgBizCodeEnum.LOTTERY_RESULT.getCode());
                msgBody.setData(result.toJSONString());
                bodies.add(msgBody);
            }
            imRouterRpc.batchSendMsg(bodies);
        } catch (Exception e) {
            LOGGER.error("[settle] broadcast error, roomId={}", roomId, e);
        }
    }
}
