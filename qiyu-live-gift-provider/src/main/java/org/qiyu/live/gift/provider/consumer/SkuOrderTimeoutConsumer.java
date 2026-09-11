package org.qiyu.live.gift.provider.consumer;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.qiyu.live.common.interfaces.dto.SkuOrderMqDTO;
import org.qiyu.live.common.interfaces.topic.GiftProviderTopicNames;
import org.qiyu.live.framework.mq.starter.properties.RocketMQConsumerProperties;
import org.qiyu.live.gift.provider.service.ISkuOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

/**
 * SKU订单超时消费者
 * 处理订单超时回滚逻辑
 */
@Configuration
public class SkuOrderTimeoutConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkuOrderTimeoutConsumer.class);

    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @Resource
    private ISkuOrderService skuOrderService;

    @Override
    public void afterPropertiesSet() throws Exception {
        DefaultMQPushConsumer mqPushConsumer = new DefaultMQPushConsumer();
        mqPushConsumer.setVipChannelEnabled(false);
        mqPushConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        mqPushConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_SkuOrderTimeout");
        mqPushConsumer.setConsumeMessageBatchMaxSize(10);
        mqPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        // 监听订单超时消息
        mqPushConsumer.subscribe(GiftProviderTopicNames.SKU_ORDER_TIMEOUT, "");
        mqPushConsumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    SkuOrderMqDTO mqDTO = JSON.parseObject(new String(msg.getBody()), SkuOrderMqDTO.class);
                    LOGGER.info("[SkuOrderTimeoutConsumer] 收到订单超时消息, orderId={}", mqDTO.getOrderId());

                    // 调用订单服务进行超时回滚
                    skuOrderService.timeoutRollback(mqDTO.getOrderId());
                } catch (Exception e) {
                    LOGGER.error("[SkuOrderTimeoutConsumer] 处理订单超时失败", e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
        LOGGER.info("[SkuOrderTimeoutConsumer] SKU订单超时消费者启动成功");
    }
}
