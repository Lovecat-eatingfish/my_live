package org.qiyu.live.stream.interfaces.rpc;

import org.qiyu.live.stream.interfaces.dto.LivingStreamPushUrlDTO;
import org.qiyu.live.stream.interfaces.dto.StreamStatusDTO;

/**
 * 直播推流服务接口（主播侧）
 */
public interface ILivingStreamRpc {

    /**
     * 生成推流地址
     *
     * @param roomId   房间ID
     * @param anchorId 主播ID
     * @return 推流地址信息
     */
    LivingStreamPushUrlDTO createPushUrl(Integer roomId, Long anchorId);

    /**
     * 连麦观众第二路推流地址（streamKey 前缀 liveg_，走 Redis 反查 roomId，不占房间主 stream_key）
     */
    LivingStreamPushUrlDTO createGuestPushUrl(Integer roomId, Long guestUserId);

    /**
     * 查询流状态
     *
     * @param roomId 房间ID
     * @return 流状态
     */
    StreamStatusDTO getStreamStatus(Integer roomId);

    /**
     * 主动停止推流（关播时调用）
     *
     * @param roomId 房间ID
     * @return 是否成功
     */
    boolean stopStream(Integer roomId);
}
