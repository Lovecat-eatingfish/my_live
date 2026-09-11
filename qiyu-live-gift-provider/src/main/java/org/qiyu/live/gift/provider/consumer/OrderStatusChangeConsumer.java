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
import org.qiyu.live.common.interfaces.dto.SkuOrderMqDTO;
import org.qiyu.live.common.interfaces.topic.GiftProviderTopicNames;
import org.qiyu.live.framework.mq.starter.properties.RocketMQConsumerProperties;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单状态变更消费者
 * 处理订单状态变更通知，通过IM推送给用户
 */
@Configuration
public class OrderStatusChangeConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderStatusChangeConsumer.class);

    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @DubboReference
    private ImRouterRpc routerRpc;

    @Override
    public void afterPropertiesSet() throws Exception {
        DefaultMQPushConsumer mqPushConsumer = new DefaultMQPushConsumer();
        mqPushConsumer.setVipChannelEnabled(false);
        mqPushConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        mqPushConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_OrderStatusChange");
        mqPushConsumer.setConsumeMessageBatchMaxSize(10);
        mqPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        mqPushConsumer.subscribe(GiftProviderTopicNames.ORDER_STATUS_CHANGE, "");
        mqPushConsumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    SkuOrderMqDTO mqDTO = JSON.parseObject(new String(msg.getBody()), SkuOrderMqDTO.class);
                    LOGGER.info("[OrderStatusChangeConsumer] 收到订单状态变更消息, orderId={}, type={}", mqDTO.getOrderId(), mqDTO.getType());

                    // 通知用户订单状态变更
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("orderId", mqDTO.getOrderId());
                    jsonObject.put("status", mqDTO.getType());

                    ImMsgBody imMsgBody = new ImMsgBody();
                    imMsgBody.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
                    imMsgBody.setBizCode(ImMsgBizCodeEnum.ORDER_STATUS_CHANGE.getCode());
                    imMsgBody.setUserId(mqDTO.getUserId());
                    imMsgBody.setData(jsonObject.toJSONString());

                    routerRpc.sendMsg(imMsgBody);
                    LOGGER.info("[OrderStatusChangeConsumer] 订单状态变更通知已发送, userId={}", mqDTO.getUserId());
                } catch (Exception e) {
                    LOGGER.error("[OrderStatusChangeConsumer] 处理订单状态变更失败", e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
        LOGGER.info("[OrderStatusChangeConsumer] 订单状态变更消费者启动成功");
    }
}
