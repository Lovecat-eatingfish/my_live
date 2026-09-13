package org.qiyu.live.msg.provider.consumer.handler.impl;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.idea.qiyu.live.framework.redis.starter.key.MsgProviderCacheKeyBuilder;
import org.qiyu.live.common.interfaces.constants.UserLevelConstants;
import org.qiyu.live.common.interfaces.dto.UserExpChangeMqDTO;
import org.qiyu.live.common.interfaces.topic.UserProviderTopicNames;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.constants.RiskConstants;
import org.qiyu.live.common.interfaces.dto.DmMessageDTO;
import org.qiyu.live.common.interfaces.dto.RiskCheckRespDTO;
import org.qiyu.live.msg.provider.dao.mapper.UserDmConversationMapper;
import org.qiyu.live.msg.provider.dao.mapper.UserDmMessageMapper;
import org.qiyu.live.msg.provider.dao.po.UserDmMessagePO;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.msg.dto.MessageDTO;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.msg.provider.consumer.handler.MessageHandler;
import org.qiyu.live.msg.provider.service.risk.RiskCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author idea
 * @Date: Created in 22:41 2023/7/14
 * @Description
 */
@Component
public class SingleMessageHandlerImpl implements MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleMessageHandlerImpl.class);

    @DubboReference(check = false)
    private ImRouterRpc routerRpc;
    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;
    @Resource
    private RiskCheckService riskCheckService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    @Resource
    private MQProducer mqProducer;
    @Resource
    private MsgProviderCacheKeyBuilder msgProviderCacheKeyBuilder;
    @Resource
    private UserDmMessageMapper dmMapper;
    @Resource
    private UserDmConversationMapper conversationMapper;

    /** 弹幕经验每日上限（超过后不再结算经验，弹幕本身不受影响） */
    private static final int DANMU_EXP_DAILY_LIMIT = 20;


    @Override
    public void onMsgReceive(ImMsgBody imMsgBody) {
        int bizCode = imMsgBody.getBizCode();
        //直播间的聊天消息
        if (ImMsgBizCodeEnum.LIVING_ROOM_IM_CHAT_MSG_BIZ.getCode() == bizCode) {
            MessageDTO messageDTO = JSON.parseObject(imMsgBody.getData(), MessageDTO.class);
            Integer roomId = messageDTO.getRoomId();
            //风控三道闸：禁言 → 频率 → 敏感词（替换/拦截），任一不通过都不广播
            if (isMuted(imMsgBody.getUserId())) {
                sendBlockedNotice(imMsgBody, roomId, "您已被禁言，暂时无法发言");
                return;
            }
            if (!riskCheckService.checkDanmuFreq(imMsgBody.getUserId())) {
                return;
            }
            RiskCheckRespDTO riskResp = riskCheckService.checkText(messageDTO.getContent(), RiskConstants.SCENE_DANMU);
            if (riskResp.isBlocked()) {
                LOGGER.info("[onMsgReceive] danmu blocked, userId={}, roomId={}", imMsgBody.getUserId(), roomId);
                sendBlockedNotice(imMsgBody, roomId, "消息包含敏感内容，已被拦截");
                return;
            }
            if (riskResp.getReplacedText() != null) {
                messageDTO.setContent(riskResp.getReplacedText());
            }
            // 等级徽章随弹幕下发 + 弹幕经验（每日上限，超限只丢经验不丢弹幕）
            messageDTO.setLevel(getUserLevel(imMsgBody.getUserId()));
            sendDanmuExp(imMsgBody.getUserId(), roomId);
            //一个人发送 n个人接收
            // 根据roomId，appId 去调用rpc方法，获取对应的直播间内的userId
            // 创建一个list的imMsgBody对象，
            LivingRoomReqDTO reqDTO = new LivingRoomReqDTO();
            reqDTO.setRoomId(roomId);
            reqDTO.setAppId(imMsgBody.getAppId());
            //自己不用发
            List<Long> userIdList = livingRoomRpc.queryUserIdByRoomId(reqDTO).stream().filter(x->!x.equals(imMsgBody.getUserId())).collect(Collectors.toList());
            if(CollectionUtils.isEmpty(userIdList)) {
                return;
            }
            List<ImMsgBody> imMsgBodies = new ArrayList<>();
            userIdList.forEach(userId -> {
                ImMsgBody respMsg = new ImMsgBody();
                respMsg.setUserId(userId);
                respMsg.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
                respMsg.setBizCode(ImMsgBizCodeEnum.LIVING_ROOM_IM_CHAT_MSG_BIZ.getCode());
                respMsg.setData(JSON.toJSONString(messageDTO));
                imMsgBodies.add(respMsg);
            });
            //暂时不做过多的处理
            routerRpc.batchSendMsg(imMsgBodies);
        } else if (ImMsgBizCodeEnum.DM_MSG_UP.getCode() == bizCode) {
            handleDmUp(imMsgBody);
        }
    }

    /**
     * 私信上行（bizCode=5568）：风控 → 落库+会话 upsert → 下行 5569 给接收方，并回显给发送方
     * （发送方 UI 只以服务端回显为准追加，天然有序且跨端一致）
     */
    private void handleDmUp(ImMsgBody imMsgBody) {
        DmMessageDTO dm;
        try {
            dm = JSON.parseObject(imMsgBody.getData(), DmMessageDTO.class);
        } catch (Exception e) {
            LOGGER.warn("[handleDmUp] bad data, userId={}", imMsgBody.getUserId());
            return;
        }
        if (dm == null || dm.getToUid() == null || !StringUtils.hasText(dm.getContent())) {
            return;
        }
        Long fromUid = imMsgBody.getUserId();
        Long toUid = dm.getToUid();
        //fromUid 以连接鉴权结果为准，防伪造；自己发给自己忽略
        if (fromUid.equals(toUid) || dm.getContent().length() > 500) {
            return;
        }
        RiskCheckRespDTO riskResp = riskCheckService.checkText(dm.getContent(), RiskConstants.SCENE_DANMU);
        if (riskResp.isBlocked()) {
            sendBlockedNotice(imMsgBody, null, "私信包含敏感内容，已被拦截");
            return;
        }
        String content = riskResp.getReplacedText() != null ? riskResp.getReplacedText() : dm.getContent();
        try {
            UserDmMessagePO msgPO = new UserDmMessagePO();
            msgPO.setFromUid(fromUid);
            msgPO.setToUid(toUid);
            msgPO.setContent(content);
            msgPO.setStatus(1);
            dmMapper.insert(msgPO);

            //会话双方各一行：发送方 unread+0，接收方 unread+1
            conversationMapper.upsertOnNewMsg(fromUid, toUid, content, 0);
            conversationMapper.upsertOnNewMsg(toUid, fromUid, content, 1);

            DmMessageDTO down = new DmMessageDTO();
            down.setMsgId(msgPO.getId());
            down.setFromUid(fromUid);
            down.setToUid(toUid);
            down.setContent(content);
            down.setCreateTime(msgPO.getCreateTime());
            String downData = JSON.toJSONString(down);
            //接收方在线推送（离线自然丢弃，靠会话未读数兜底）
            ImMsgBody toMsg = new ImMsgBody();
            toMsg.setUserId(toUid);
            toMsg.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            toMsg.setBizCode(ImMsgBizCodeEnum.DM_MSG_DOWN.getCode());
            toMsg.setData(downData);
            //发送方回显
            ImMsgBody echoMsg = new ImMsgBody();
            echoMsg.setUserId(fromUid);
            echoMsg.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            echoMsg.setBizCode(ImMsgBizCodeEnum.DM_MSG_DOWN.getCode());
            echoMsg.setData(downData);
            routerRpc.batchSendMsg(java.util.Arrays.asList(toMsg, echoMsg));
        } catch (Exception e) {
            LOGGER.error("[handleDmUp] persist/send error, from={}, to={}", fromUid, toUid, e);
        }
    }

    private boolean isMuted(Long userId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(RiskConstants.BAN_MUTE_KEY_PREFIX + userId));
        } catch (Exception e) {
            LOGGER.error("[isMuted] redis error, userId={}", userId, e);
            return false;
        }
    }

    /**
     * 风控提示单发给发送者本人（bizCode=5566），不进房间广播
     */
    private void sendBlockedNotice(ImMsgBody originMsg, Integer roomId, String reason) {
        MessageDTO notice = new MessageDTO();
        notice.setUserId(originMsg.getUserId());
        notice.setRoomId(roomId);
        notice.setType(5566);
        notice.setContent(reason);
        ImMsgBody respMsg = new ImMsgBody();
        respMsg.setUserId(originMsg.getUserId());
        respMsg.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        respMsg.setBizCode(ImMsgBizCodeEnum.RISK_MSG_BLOCKED.getCode());
        respMsg.setData(JSON.toJSONString(notice));
        routerRpc.batchSendMsg(Collections.singletonList(respMsg));
    }

    /** 读取用户等级（user-provider 升级时写入），缓存未命中时不下发徽章 */
    private Integer getUserLevel(Long userId) {
        try {
            String level = stringRedisTemplate.opsForValue().get(UserLevelConstants.LEVEL_KEY_PREFIX + userId);
            return level == null ? null : Integer.valueOf(level);
        } catch (Exception e) {
            LOGGER.error("[getUserLevel] redis error, userId={}", userId, e);
            return null;
        }
    }

    /** 弹幕经验：每日前 DANMU_EXP_DAILY_LIMIT 条每条 +1，经 MQ 交 user-provider 单点结算 */
    private void sendDanmuExp(Long userId, Integer roomId) {
        try {
            String dayKey = msgProviderCacheKeyBuilder.buildDanmuExpDailyKey(userId,
                    java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
            Long cnt = stringRedisTemplate.opsForValue().increment(dayKey);
            if (cnt != null && cnt == 1) {
                stringRedisTemplate.expire(dayKey, java.time.Duration.ofHours(25));
            }
            if (cnt == null || cnt > DANMU_EXP_DAILY_LIMIT) {
                return;
            }
            UserExpChangeMqDTO expDTO = UserExpChangeMqDTO.of(userId, 1, UserLevelConstants.EXP_SCENE_DANMU, roomId);
            mqProducer.send(new Message(UserProviderTopicNames.USER_EXP_CHANGE_TOPIC,
                    JSON.toJSONBytes(expDTO)));
        } catch (Exception e) {
            // 经验结算失败不影响弹幕主链路
            LOGGER.error("[sendDanmuExp] error, userId={}", userId, e);
        }
    }
}
