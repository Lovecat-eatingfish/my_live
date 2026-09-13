package org.qiyu.live.stream.provider.service;

import org.qiyu.live.stream.interfaces.dto.LivingStreamPushUrlDTO;
import org.qiyu.live.stream.interfaces.dto.StreamStatusDTO;

/**
 * 直播推流服务接口
 */
public interface ILivingStreamService {

    /**
     * 生成推流地址
     * @param roomId   房间ID
     * @param anchorId 主播ID
     * @return 推流地址信息
     */
    LivingStreamPushUrlDTO createPushUrl(Integer roomId, Long anchorId);

    /** 连麦观众第二路推流地址 */
    LivingStreamPushUrlDTO createGuestPushUrl(Integer roomId, Long guestUserId);

    /**
     * 查询流状态
     * @param roomId 房间ID
     * @return 流状态
     */
    StreamStatusDTO getStreamStatus(Integer roomId);

    /**
     * 主动停止推流（关播时调用）
     * @param roomId 房间ID
     * @return 是否成功
     */
    boolean stopStream(Integer roomId);

    /**
     * SRS 回调：推流开始
     * @param streamKey 流标识
     * @param clientId   SRS 客户端ID
     * @param ip         推流客户端IP
     */
    void onPublish(String streamKey, String clientId, String ip);

    /**
     * SRS 回调：推流结束
     * @param streamKey 流标识
     */
    void onUnpublish(String streamKey);
}
