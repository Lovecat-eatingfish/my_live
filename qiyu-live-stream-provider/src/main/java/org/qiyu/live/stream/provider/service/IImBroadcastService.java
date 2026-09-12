package org.qiyu.live.stream.provider.service;

import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;

import com.alibaba.fastjson.JSONObject;

import java.util.List;

/**
 * IM 批量广播服务（推流状态/回放通知等）
 */
public interface IImBroadcastService {

    /**
     * 向直播间所有用户广播业务消息
     *
     * @param roomId 房间ID
     * @param bizCode 业务消息码
     * @param data    消息体
     * @return 实际推送的用户数（-1 表示推送失败）
     */
    int broadcastToRoom(Integer roomId, ImMsgBizCodeEnum bizCode, JSONObject data);

    /**
     * 向指定用户批量发送业务消息
     */
    void batchSend(List<Long> userIdList, ImMsgBizCodeEnum bizCode, JSONObject data);
}
