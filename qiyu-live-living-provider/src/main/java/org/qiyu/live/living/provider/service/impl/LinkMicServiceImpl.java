package org.qiyu.live.living.provider.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.qiyu.live.living.provider.dao.mapper.ILivingLinkMicMapper;
import org.qiyu.live.living.provider.dao.po.LivingLinkMicPO;
import org.qiyu.live.living.provider.service.ILinkMicService;
import org.qiyu.live.stream.interfaces.dto.LivingStreamPushUrlDTO;
import org.qiyu.live.stream.interfaces.rpc.ILivingStreamRpc;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 连麦状态机：invite(0) → accept(1) → hangUp(2)。
 * 信令 5572 单发给被邀请人（invite），广播全房间（start/stop）。
 * 推流地址由 stream-provider liveg_ 前缀 key 生成，与房间主推流互不干扰。
 */
@Service
public class LinkMicServiceImpl implements ILinkMicService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkMicServiceImpl.class);

    private static final int STATUS_INVITING = 0;
    private static final int STATUS_LIVING = 1;
    private static final int STATUS_ENDED = 2;

    @Resource
    private ILivingLinkMicMapper linkMicMapper;
    @DubboReference(check = false)
    private ImRouterRpc imRouterRpc;
    @DubboReference(check = false)
    private IUserRpc userRpc;
    @DubboReference(check = false)
    private ILivingStreamRpc livingStreamRpc;

    @Override
    public Long invite(Integer roomId, Long guestUserId) {
        // 同一观众有待处理邀请或已在连麦则拒绝重复邀请
        Long exists = linkMicMapper.selectCount(new LambdaQueryWrapper<LivingLinkMicPO>()
                .eq(LivingLinkMicPO::getRoomId, roomId)
                .eq(LivingLinkMicPO::getGuestUserId, guestUserId)
                .in(LivingLinkMicPO::getStatus, STATUS_INVITING, STATUS_LIVING));
        if (exists > 0) {
            return null;
        }
        LivingLinkMicPO po = new LivingLinkMicPO();
        po.setRoomId(roomId);
        po.setGuestUserId(guestUserId);
        po.setStatus(STATUS_INVITING);
        po.setCreateTime(new Date());
        linkMicMapper.insert(po);

        JSONObject data = new JSONObject();
        data.put("action", "invite");
        data.put("roomId", roomId);
        data.put("linkMicId", po.getId());
        sendSignal(guestUserId, data);
        LOGGER.info("[invite] roomId={}, guestUserId={}, linkMicId={}", roomId, guestUserId, po.getId());
        return po.getId();
    }

    @Override
    public boolean accept(Long linkMicId) {
        LivingLinkMicPO po = linkMicMapper.selectById(linkMicId);
        if (po == null || po.getStatus() != STATUS_INVITING) {
            return false;
        }
        linkMicMapper.update(null, new LambdaUpdateWrapper<LivingLinkMicPO>()
                .eq(LivingLinkMicPO::getId, linkMicId)
                .eq(LivingLinkMicPO::getStatus, STATUS_INVITING)
                .set(LivingLinkMicPO::getStatus, STATUS_LIVING)
                .set(LivingLinkMicPO::getStartTime, new Date()));

        // 第二路推流地址（WebRTC 发布参数）单发给观众
        LivingStreamPushUrlDTO pushUrlDTO = livingStreamRpc.createGuestPushUrl(po.getRoomId(), po.getGuestUserId());
        JSONObject guestData = new JSONObject();
        guestData.put("action", "accepted");
        guestData.put("roomId", po.getRoomId());
        guestData.put("linkMicId", linkMicId);
        guestData.put("rtcPublishApi", pushUrlDTO.getRtcPublishApi());
        guestData.put("rtcStreamUrl", pushUrlDTO.getRtcStreamUrl());
        guestData.put("hlsUrl", pushUrlDTO.getHlsUrl());
        sendSignal(po.getGuestUserId(), guestData);

        // 全房间广播连麦开始（观众端加第二画面）
        UserDTO guest = userRpc.getByUserId(po.getGuestUserId());
        JSONObject roomData = new JSONObject();
        roomData.put("action", "start");
        roomData.put("roomId", po.getRoomId());
        roomData.put("guestUserId", po.getGuestUserId());
        roomData.put("guestNickName", guest == null || guest.getNickName() == null
                ? ("用户" + po.getGuestUserId()) : guest.getNickName());
        roomData.put("hlsUrl", pushUrlDTO.getHlsUrl());
        broadcast(po.getRoomId(), roomData);
        LOGGER.info("[accept] linkMicId={}, roomId={}", linkMicId, po.getRoomId());
        return true;
    }

    @Override
    public boolean hangUp(Integer roomId) {
        LivingLinkMicPO po = linkMicMapper.selectOne(new LambdaQueryWrapper<LivingLinkMicPO>()
                .eq(LivingLinkMicPO::getRoomId, roomId)
                .eq(LivingLinkMicPO::getStatus, STATUS_LIVING)
                .orderByDesc(LivingLinkMicPO::getId)
                .last("limit 1"));
        if (po == null) {
            return false;
        }
        linkMicMapper.update(null, new LambdaUpdateWrapper<LivingLinkMicPO>()
                .eq(LivingLinkMicPO::getId, po.getId())
                .set(LivingLinkMicPO::getStatus, STATUS_ENDED)
                .set(LivingLinkMicPO::getEndTime, new Date()));
        JSONObject data = new JSONObject();
        data.put("action", "stop");
        data.put("roomId", roomId);
        data.put("guestUserId", po.getGuestUserId());
        broadcast(roomId, data);
        LOGGER.info("[hangUp] roomId={}, linkMicId={}", roomId, po.getId());
        return true;
    }

    private void sendSignal(Long userId, JSONObject data) {
        ImMsgBody body = new ImMsgBody();
        body.setUserId(userId);
        body.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        body.setBizCode(ImMsgBizCodeEnum.LINK_MIC_SIGNAL.getCode());
        body.setData(data.toJSONString());
        imRouterRpc.batchSendMsg(Collections.singletonList(body));
    }

    private void broadcast(Integer roomId, JSONObject data) {
        // 查询房间内所有在线用户广播；房间已无人在线时至少不报错
        org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO reqDTO = new org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO();
        reqDTO.setRoomId(roomId);
        reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        List<Long> userIds;
        try {
            userIds = livingRoomUserIds(roomId);
        } catch (Exception e) {
            userIds = null;
        }
        if (userIds == null || userIds.isEmpty()) {
            LOGGER.info("[broadcast] room empty, skip, roomId={}", roomId);
            return;
        }
        for (Long userId : userIds) {
            ImMsgBody body = new ImMsgBody();
            body.setUserId(userId);
            body.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            body.setBizCode(ImMsgBizCodeEnum.LINK_MIC_SIGNAL.getCode());
            body.setData(data.toJSONString());
            imRouterRpc.batchSendMsg(Collections.singletonList(body));
        }
    }

    @Resource
    private org.qiyu.live.living.provider.service.ILivingRoomService livingRoomService;

    /** 复用房间用户集合查询（同模块直调） */
    private List<Long> livingRoomUserIds(Integer roomId) {
        org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO reqDTO = new org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO();
        reqDTO.setRoomId(roomId);
        reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        return livingRoomService.queryUserIdByRoomId(reqDTO);
    }
}
