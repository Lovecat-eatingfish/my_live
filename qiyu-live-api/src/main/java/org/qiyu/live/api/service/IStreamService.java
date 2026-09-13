package org.qiyu.live.api.service;

import org.qiyu.live.api.vo.StreamPlayUrlVO;
import org.qiyu.live.api.vo.StreamPushUrlVO;
import org.qiyu.live.api.vo.StreamRecordVO;
import org.qiyu.live.api.vo.StreamStatusVO;

import java.util.List;

/**
 * 直播视频流服务
 */
public interface IStreamService {

    /**
     * 生成推流地址（仅主播本人可调用）
     *
     * @param roomId 房间ID
     * @return 推流地址信息
     */
    StreamPushUrlVO createPushUrl(Integer roomId);

    /**
     * 查询房间流状态
     *
     * @param roomId 房间ID
     * @return 流状态
     */
    StreamStatusVO getStreamStatus(Integer roomId);

    /**
     * 获取观众端播放地址
     *
     * @param roomId 房间ID
     * @return 播放地址信息
     */
    StreamPlayUrlVO getPlayUrl(Integer roomId);

    /**
     * 获取录制回放列表
     *
     * @param roomId 房间ID
     * @return 回放记录列表
     */
    List<StreamRecordVO> getRecordList(Integer roomId);

    /** 主播主页回放列表 */
    List<StreamRecordVO> getRecordListByAnchor(Long anchorId);
}
