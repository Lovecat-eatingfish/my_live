package org.qiyu.live.living.provider.consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.qiyu.live.common.interfaces.constants.VoteConstants;
import org.qiyu.live.framework.mq.starter.properties.RocketMQConsumerProperties;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 投票结算消费者：createVote 发对应时长档位延迟消息，到点广播 5577 结果并清理 Redis。
 */
@Component
public class VoteSettleConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoteSettleConsumer.class);

    public static final String VOTE_SETTLE_TOPIC = "LivingVoteSettleTopic";

    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @DubboReference(check = false)
    private ImRouterRpc imRouterRpc;

    @Override
    public void afterPropertiesSet() throws Exception {
        DefaultMQPushConsumer mqPushConsumer = new DefaultMQPushConsumer();
        mqPushConsumer.setVipChannelEnabled(false);
        mqPushConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        mqPushConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_" + this.getClass().getSimpleName());
        mqPushConsumer.subscribe(VOTE_SETTLE_TOPIC, "");
        mqPushConsumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    // 发送侧 body 是裸 roomId 字符串
                    JSONObject body = new JSONObject();
                    body.put("roomId", Integer.parseInt(new String(msg.getBody()).trim()));
                    settle(body);
                } catch (Exception e) {
                    LOGGER.error("[vote settle] error, msg={}", new String(msg.getBody()), e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
        LOGGER.info("vote settle consumer started");
    }

    private void settle(JSONObject body) {
        Integer roomId = body.getInteger("roomId");
        if (roomId == null) {
            return;
        }
        String ctxKey = VoteConstants.VOTE_CTX_KEY_PREFIX + roomId;
        String countsKey = VoteConstants.VOTE_COUNTS_KEY_PREFIX + roomId;
        String votedKey = VoteConstants.VOTE_VOTED_KEY_PREFIX + roomId;
        String ctxStr = stringRedisTemplate.opsForValue().get(ctxKey);
        if (ctxStr == null) {
            return;
        }
        JSONObject ctx = JSON.parseObject(ctxStr);
        stringRedisTemplate.delete(ctxKey);

        List<String> options = ctx.getJSONArray("options").toJavaList(String.class);
        java.util.Set<String> voters = stringRedisTemplate.opsForSet().members(votedKey);
        java.util.List<Long> counts = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            Object c = stringRedisTemplate.opsForHash().get(countsKey, String.valueOf(i));
            counts.add(c == null ? 0 : Long.parseLong(c.toString()));
        }
        stringRedisTemplate.delete(countsKey);
        stringRedisTemplate.delete(votedKey);

        JSONObject result = new JSONObject();
        result.put("roomId", roomId);
        result.put("title", ctx.getString("title"));
        result.put("options", options);
        result.put("counts", counts);
        result.put("votedCount", voters == null ? 0 : voters.size());

        // 广播给投过票的观众 + 主播
        try {
            List<ImMsgBody> bodies = new ArrayList<>();
            List<Long> targets = new ArrayList<>();
            targets.add(ctx.getLong("anchorId"));
            if (voters != null) {
                voters.forEach(v -> targets.add(Long.valueOf(v)));
            }
            for (Long uid : targets) {
                ImMsgBody mb = new ImMsgBody();
                mb.setUserId(uid);
                mb.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
                mb.setBizCode(ImMsgBizCodeEnum.VOTE_RESULT.getCode());
                mb.setData(result.toJSONString());
                bodies.add(mb);
            }
            imRouterRpc.batchSendMsg(bodies);
        } catch (Exception e) {
            LOGGER.error("[vote settle] broadcast error, roomId={}", roomId, e);
        }
        LOGGER.info("[vote settle] roomId={} counts={}", roomId, counts);
    }
}
