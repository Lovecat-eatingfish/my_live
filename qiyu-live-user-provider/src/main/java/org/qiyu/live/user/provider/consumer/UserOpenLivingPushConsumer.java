package org.qiyu.live.user.provider.consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.qiyu.live.user.provider.config.RocketMQConsumerProperties;
import org.qiyu.live.common.interfaces.dto.OpenLivingPushMqDTO;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.common.interfaces.topic.UserProviderTopicNames;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.provider.dao.mapper.IUserNotifyMapper;
import org.qiyu.live.user.provider.dao.po.UserNotifyPO;
import org.qiyu.live.user.provider.service.IUserRelationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 开播推送消费者：主播开播（api 层发 MQ）→ 分页拉粉丝 → 5567 在线推送 + 站内通知落库
 * 不在线的直接丢弃（不做离线推送）。
 */
@Component
public class UserOpenLivingPushConsumer implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserOpenLivingPushConsumer.class);

    private static final int FANS_PAGE_SIZE = 500;
    private static final int IM_CHUNK_SIZE = 200;

    /** 通知类型：开播通知 */
    private static final int NOTIFY_TYPE_OPEN_LIVING = 4;

    @Resource
    private RocketMQConsumerProperties rocketMQConsumerProperties;
    @Resource
    private IUserRelationService userRelationService;
    @Resource
    private IUserNotifyMapper userNotifyMapper;
    @DubboReference(check = false)
    private ImRouterRpc routerRpc;

    @Override
    public void afterPropertiesSet() throws Exception {
        DefaultMQPushConsumer mqPushConsumer = new DefaultMQPushConsumer();
        mqPushConsumer.setVipChannelEnabled(false);
        mqPushConsumer.setNamesrvAddr(rocketMQConsumerProperties.getNameSrv());
        mqPushConsumer.setConsumerGroup(rocketMQConsumerProperties.getGroupName() + "_" + this.getClass().getSimpleName());
        mqPushConsumer.setConsumeMessageBatchMaxSize(1);
        mqPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        mqPushConsumer.subscribe(UserProviderTopicNames.OPEN_LIVING_PUSH_TOPIC, "");
        mqPushConsumer.setMessageListener((MessageListenerConcurrently) (List<MessageExt> msgs, ConsumeConcurrentlyContext context) -> {
            for (MessageExt msg : msgs) {
                try {
                    OpenLivingPushMqDTO dto = JSON.parseObject(new String(msg.getBody()), OpenLivingPushMqDTO.class);
                    pushToFans(dto);
                } catch (Exception e) {
                    LOGGER.error("[consume] open living push error, msg={}", new String(msg.getBody()), e);
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        mqPushConsumer.start();
        LOGGER.info("开播推送消费者启动成功,namesrv is {}", rocketMQConsumerProperties.getNameSrv());
    }

    private void pushToFans(OpenLivingPushMqDTO dto) {
        JSONObject data = new JSONObject();
        data.put("anchorId", dto.getAnchorId());
        data.put("anchorName", dto.getAnchorName());
        data.put("roomId", dto.getRoomId());
        data.put("roomName", dto.getRoomName());
        data.put("cover", dto.getCover());
        int page = 1;
        int notifyCount = 0;
        while (true) {
            PageWrapper<UserDTO> fanPage = userRelationService.pageFans(dto.getAnchorId(), page, FANS_PAGE_SIZE);
            List<UserDTO> fans = fanPage.getList();
            if (fans == null || fans.isEmpty()) {
                break;
            }
            List<ImMsgBody> msgBodies = new ArrayList<>();
            for (UserDTO fan : fans) {
                ImMsgBody msgBody = new ImMsgBody();
                msgBody.setUserId(fan.getUserId());
                msgBody.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
                msgBody.setBizCode(ImMsgBizCodeEnum.OPEN_LIVING_PUSH.getCode());
                msgBody.setData(data.toJSONString());
                msgBodies.add(msgBody);
                notifyCount++;
                if (msgBodies.size() >= IM_CHUNK_SIZE) {
                    routerRpc.batchSendMsg(new ArrayList<>(msgBodies));
                    msgBodies.clear();
                }
                UserNotifyPO notifyPO = new UserNotifyPO();
                notifyPO.setUserId(fan.getUserId());
                notifyPO.setType(NOTIFY_TYPE_OPEN_LIVING);
                notifyPO.setTitle("关注的主播开播了");
                notifyPO.setContent((dto.getAnchorName() == null ? "主播" + dto.getAnchorId() : dto.getAnchorName())
                        + " 开播啦，快去围观~");
                notifyPO.setJumpUrl("/room/" + dto.getRoomId());
                notifyPO.setIsRead(0);
                notifyPO.setCreateTime(new Date());
                userNotifyMapper.insert(notifyPO);
            }
            if (!msgBodies.isEmpty()) {
                routerRpc.batchSendMsg(msgBodies);
            }
            if (!fanPage.isHasNext()) {
                break;
            }
            page++;
        }
        LOGGER.info("[pushToFans] anchorId={} roomId={} 粉丝推送/通知 {} 条", dto.getAnchorId(), dto.getRoomId(), notifyCount);
    }
}
