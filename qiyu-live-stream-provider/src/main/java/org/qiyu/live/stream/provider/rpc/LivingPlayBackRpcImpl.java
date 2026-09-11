package org.qiyu.live.stream.provider.rpc;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.stream.interfaces.dto.LivingRoomRecordDTO;
import org.qiyu.live.stream.interfaces.dto.PlayBackDTO;
import org.qiyu.live.stream.interfaces.rpc.ILivingPlayBackRpc;
import org.qiyu.live.stream.provider.service.ILivingPlayBackService;

import java.util.List;

/**
 * 直播播放 RPC 实现
 */
@DubboService
public class LivingPlayBackRpcImpl implements ILivingPlayBackRpc {

    @Resource
    private ILivingPlayBackService livingPlayBackService;

    @Override
    public PlayBackDTO getPlayUrl(Integer roomId, String networkType) {
        return livingPlayBackService.getPlayUrl(roomId, networkType);
    }

    @Override
    public List<LivingRoomRecordDTO> getRecordList(Integer roomId) {
        return livingPlayBackService.getRecordList(roomId);
    }
}
