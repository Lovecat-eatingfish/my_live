package org.qiyu.live.stream.provider.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.stream.provider.service.IImBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * IM 批量广播服务实现
 * 复用送礼消息的推送链路：living-provider 查房间用户 -> im-router 批量下发
 */
@Service
public class ImBroadcastServiceImpl implements IImBroadcastService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImBroadcastServiceImpl.class);

    // living-provider / im-router 可能未启动，降级为调用时才报错，不阻塞本服务启动
    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;
    @DubboReference(check = false)
    private ImRouterRpc imRouterRpc;

    @Override
    public int broadcastToRoom(Integer roomId, ImMsgBizCodeEnum bizCode, JSONObject data) {
        try {
            LivingRoomReqDTO reqDTO = new LivingRoomReqDTO();
            reqDTO.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            reqDTO.setRoomId(roomId);
            List<Long> userIdList = livingRoomRpc.queryUserIdByRoomId(reqDTO);
            if (CollectionUtils.isEmpty(userIdList)) {
                return 0;
            }
            batchSend(userIdList, bizCode, data);
            return userIdList.size();
        } catch (Exception e) {
            LOGGER.error("[broadcastToRoom] failed, roomId={}, bizCode={}", roomId, bizCode.getCode(), e);
            return -1;
        }
    }

    @Override
    public void batchSend(List<Long> userIdList, ImMsgBizCodeEnum bizCode, JSONObject data) {
        List<ImMsgBody> imMsgBodies = userIdList.stream().map(userId -> {
            ImMsgBody imMsgBody = new ImMsgBody();
            imMsgBody.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
            imMsgBody.setBizCode(bizCode.getCode());
            imMsgBody.setUserId(userId);
            imMsgBody.setData(data.toJSONString());
            return imMsgBody;
        }).collect(Collectors.toList());
        imRouterRpc.batchSendMsg(imMsgBodies);
    }
}
