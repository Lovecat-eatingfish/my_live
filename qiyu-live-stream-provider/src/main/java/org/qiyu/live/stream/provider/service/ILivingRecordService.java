package org.qiyu.live.stream.provider.service;

/**
 * 直播录制回放服务（SRS DVR -> MinIO -> t_living_room_record）
 */
public interface ILivingRecordService {

    /**
     * 暂存录制上下文（关播/停流时调用，供稍后到达的 on_dvr 回调消费）
     *
     * @param roomId    房间ID
     * @param streamKey 流标识
     */
    void captureStreamContext(Integer roomId, String streamKey);

    /**
     * 处理 SRS on_dvr 回调：录制文件已落盘，上传 MinIO 并写入回放记录
     *
     * @param streamKey      流标识
     * @param containerFile  SRS 容器内的录制文件路径
     */
    void handleDvrFile(String streamKey, String containerFile);
}
