package org.qiyu.live.video.provider.consumer;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.qiyu.live.framework.mq.starter.properties.RocketMQConsumerProperties;
import org.qiyu.live.common.interfaces.dto.VideoTranscodeMqDTO;
import org.qiyu.live.common.interfaces.topic.VideoProviderTopicNames;
import org.qiyu.live.video.provider.service.impl.VideoTranscodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 视频转码消费者：publish 成功 → 转码任务（remux 或重编码 + 抽封面 + 传 MinIO 回写）。
 * 失败重试 2 次后置失败态（回退播原文件），不再无限重试。
 */
@Component
public class VideoTranscodeConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoTranscodeConsumer.class);

    private static final int MAX_RECONSUME = 2;

    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @Resource
    private VideoTranscodeService videoTranscodeService;

    @Override
    public void afterPropertiesSet() throws Exception {
        DefaultMQPushConsumer mqPushConsumer = new DefaultMQPushConsumer();
        mqPushConsumer.setVipChannelEnabled(false);
        mqPushConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        mqPushConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_" + this.getClass().getSimpleName());
        mqPushConsumer.setConsumeMessageBatchMaxSize(1);
        mqPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        mqPushConsumer.subscribe(VideoProviderTopicNames.VIDEO_TRANSCODE_TOPIC, "");
        mqPushConsumer.setMessageListener((MessageListenerConcurrently) (List<MessageExt> msgs, ConsumeConcurrentlyContext context) -> {
            for (MessageExt msg : msgs) {
                VideoTranscodeMqDTO dto = JSON.parseObject(new String(msg.getBody()), VideoTranscodeMqDTO.class);
                try {
                    videoTranscodeService.transcode(dto.getVideoId());
                } catch (Exception e) {
                    LOGGER.error("[consume] transcode error, videoId={}, reconsumeTimes={}",
                            dto.getVideoId(), msg.getReconsumeTimes(), e);
                    if (msg.getReconsumeTimes() >= MAX_RECONSUME) {
                        videoTranscodeService.markFailed(dto.getVideoId());
                    } else {
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
        LOGGER.info("视频转码消费者启动成功,namesrv is {}", rocketMQConsumerProperties.getNameSrv());
    }
}
