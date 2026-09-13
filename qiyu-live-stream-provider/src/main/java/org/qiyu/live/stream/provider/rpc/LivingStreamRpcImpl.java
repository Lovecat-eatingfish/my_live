package org.qiyu.live.stream.provider.rpc;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.stream.interfaces.dto.LivingStreamPushUrlDTO;
import org.qiyu.live.stream.interfaces.dto.StreamStatusDTO;
import org.qiyu.live.stream.interfaces.rpc.ILivingStreamRpc;
import org.qiyu.live.stream.provider.service.ILivingStreamService;

/**
 * 直播推流 RPC 实现
 */
@DubboService
public class LivingStreamRpcImpl implements ILivingStreamRpc {

    @Resource
    private ILivingStreamService livingStreamService;

    @Override
    public LivingStreamPushUrlDTO createPushUrl(Integer roomId, Long anchorId) {
        return livingStreamService.createPushUrl(roomId, anchorId);
    }

    @Override
    public LivingStreamPushUrlDTO createGuestPushUrl(Integer roomId, Long guestUserId) {
        return livingStreamService.createGuestPushUrl(roomId, guestUserId);
    }

    @Override
    public StreamStatusDTO getStreamStatus(Integer roomId) {
        return livingStreamService.getStreamStatus(roomId);
    }

    @Override
    public boolean stopStream(Integer roomId) {
        return livingStreamService.stopStream(roomId);
    }
}
