package org.qiyu.live.living.provider.consumer;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.qiyu.live.common.interfaces.topic.ImCoreServerProviderTopicNames;
import org.qiyu.live.framework.mq.starter.properties.RocketMQConsumerProperties;
import org.qiyu.live.im.core.server.interfaces.dto.ImOfflineDTO;
import org.qiyu.live.living.provider.service.ILivingRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 主播断线关播检查消费者：
 * 主播IM断线时投递的30s延迟消息到期后，校验主播是否回房/推流是否存活，
 * 决定是否真正关播（主播刷新浏览器不再误关播）
 */
@Component
public class LivingRoomCloseCheckConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivingRoomCloseCheckConsumer.class);

    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @Resource
    private ILivingRoomService livingRoomService;

    @Override
    public void afterPropertiesSet() throws Exception {
        DefaultMQPushConsumer mqPushConsumer = new DefaultMQPushConsumer();
        mqPushConsumer.setVipChannelEnabled(false);
        mqPushConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        mqPushConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_" + LivingRoomCloseCheckConsumer.class.getSimpleName());
        mqPushConsumer.setConsumeMessageBatchMaxSize(1);
        mqPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        mqPushConsumer.subscribe(ImCoreServerProviderTopicNames.LIVING_ROOM_CLOSE_CHECK, "");
        mqPushConsumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    livingRoomService.closeLivingCheck(JSON.parseObject(new String(msg.getBody()), ImOfflineDTO.class));
                } catch (Exception e) {
                    LOGGER.error("[LivingRoomCloseCheckConsumer] 关播检查失败", e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
        LOGGER.info("[LivingRoomCloseCheckConsumer] 关播检查消费者启动成功");
    }
}
