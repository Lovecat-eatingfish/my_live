package org.qiyu.live.msg.provider.consumer.handler.impl;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.constants.RiskConstants;
import org.qiyu.live.common.interfaces.dto.RiskCheckRespDTO;
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
}
