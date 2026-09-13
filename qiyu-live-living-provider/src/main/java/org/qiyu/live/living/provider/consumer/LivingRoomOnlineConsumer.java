package org.qiyu.live.living.provider.consumer;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.qiyu.live.common.interfaces.constants.UserLevelConstants;
import org.qiyu.live.common.interfaces.dto.UserExpChangeMqDTO;
import org.qiyu.live.common.interfaces.topic.UserProviderTopicNames;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.qiyu.live.common.interfaces.topic.ImCoreServerProviderTopicNames;
import org.qiyu.live.framework.mq.starter.properties.RocketMQConsumerProperties;
import org.qiyu.live.im.core.server.interfaces.dto.ImOnlineDTO;
import org.qiyu.live.living.provider.service.ILivingRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;


/**
 * @Author idea
 * @Date: Created in 15:04 2023/7/11
 * @Description
 */
@Component
public class LivingRoomOnlineConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivingRoomOnlineConsumer.class);
    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @Resource
    private ILivingRoomService livingRoomService;
    @Resource
    private MQProducer mqProducer;

    @Override
    public void afterPropertiesSet() throws Exception {
        DefaultMQPushConsumer mqPushConsumer = new DefaultMQPushConsumer();
        //老版本中会开启，新版本的mq不需要使用到
        mqPushConsumer.setVipChannelEnabled(false);
        mqPushConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        mqPushConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_" + LivingRoomOnlineConsumer.class.getSimpleName());
        //一次从broker中拉取10条消息到本地内存当中进行消费
        mqPushConsumer.setConsumeMessageBatchMaxSize(10);
        mqPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        //监听im发送过来的业务消息topic
        mqPushConsumer.subscribe(ImCoreServerProviderTopicNames.IM_ONLINE_TOPIC, "");
        mqPushConsumer.setMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                ImOnlineDTO imOnlineDTO = JSON.parseObject(new String(msg.getBody()), ImOnlineDTO.class);
                livingRoomService.userOnlineHandler(imOnlineDTO);
                // 看播经验 +10（进房即算，MQ 交 user-provider 单点结算）
                try {
                    UserExpChangeMqDTO expDTO = UserExpChangeMqDTO.of(imOnlineDTO.getUserId(), 10,
                            UserLevelConstants.EXP_SCENE_WATCH, imOnlineDTO.getRoomId());
                    mqProducer.send(new Message(UserProviderTopicNames.USER_EXP_CHANGE_TOPIC,
                            JSON.toJSONBytes(expDTO)));
                } catch (Exception e) {
                    LOGGER.error("[consume] send watch exp error, userId={}", imOnlineDTO.getUserId(), e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
        LOGGER.info("mq消费者启动成功,namesrv is {}", rocketMQConsumerProperties.getNameSrv());
    }
}
