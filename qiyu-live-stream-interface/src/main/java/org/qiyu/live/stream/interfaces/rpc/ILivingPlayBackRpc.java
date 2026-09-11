package org.qiyu.live.stream.interfaces.rpc;

import org.qiyu.live.stream.interfaces.dto.LivingRoomRecordDTO;
import org.qiyu.live.stream.interfaces.dto.PlayBackDTO;

import java.util.List;

/**
 * 直播播放服务接口（观众侧）
 */
public interface ILivingPlayBackRpc {

    /**
     * 获取播放地址
     *
     * @param roomId       房间ID
     * @param networkType  网络类型（mobile/wifi/other）
     * @return 播放地址信息
     */
    PlayBackDTO getPlayUrl(Integer roomId, String networkType);

    /**
     * 获取录制回放列表
     *
     * @param roomId 房间ID
     * @return 回放记录列表
     */
    List<LivingRoomRecordDTO> getRecordList(Integer roomId);
}
