package org.qiyu.live.user.provider.consumer;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.qiyu.live.user.provider.config.RocketMQConsumerProperties;
import org.qiyu.live.common.interfaces.dto.UserExpChangeMqDTO;
import org.qiyu.live.common.interfaces.topic.UserProviderTopicNames;
import org.qiyu.live.user.provider.service.IProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 经验值变更消费者：各动作方（看播/弹幕/送礼/发视频）发 MQ，此处单点结算等级
 */
@Component
public class UserExpChangeConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserExpChangeConsumer.class);

    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @Resource
    private IProfileService profileService;

    @Override
    public void afterPropertiesSet() throws Exception {
        DefaultMQPushConsumer mqPushConsumer = new DefaultMQPushConsumer();
        mqPushConsumer.setVipChannelEnabled(false);
        mqPushConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        mqPushConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_" + this.getClass().getSimpleName());
        mqPushConsumer.setConsumeMessageBatchMaxSize(1);
        mqPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        mqPushConsumer.subscribe(UserProviderTopicNames.USER_EXP_CHANGE_TOPIC, "");
        mqPushConsumer.setMessageListener((MessageListenerConcurrently) (List<MessageExt> msgs, ConsumeConcurrentlyContext context) -> {
            for (MessageExt msg : msgs) {
                try {
                    UserExpChangeMqDTO dto = JSON.parseObject(new String(msg.getBody()), UserExpChangeMqDTO.class);
                    profileService.addExp(dto);
                } catch (Exception e) {
                    LOGGER.error("[consume] exp change error, msg={}", new String(msg.getBody()), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
        LOGGER.info("经验值消费者启动成功,namesrv is {}", rocketMQConsumerProperties.getNameSrv());
    }
}
