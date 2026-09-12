package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.qiyu.live.api.service.IStreamService;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.web.starter.config.RequestLimit;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 直播视频流接口
 */
@RestController
@RequestMapping("/stream")
public class StreamController {

    @Resource
    private IStreamService streamService;

    /**
     * 主播获取推流地址（OBS 推流用）
     */
    @RequestLimit(limit = 1, second = 3, msg = "获取推流地址过于频繁，请稍后再试")
    @PostMapping("/createPushUrl")
    public WebResponseVO createPushUrl(Integer roomId) {
        ErrorAssert.isNotNull(roomId, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(streamService.createPushUrl(roomId));
    }

    /**
     * 查询房间流状态
     */
    @PostMapping("/status")
    public WebResponseVO status(Integer roomId) {
        ErrorAssert.isNotNull(roomId, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(streamService.getStreamStatus(roomId));
    }

    /**
     * 观众获取播放地址（HLS）
     */
    @PostMapping("/playUrl")
    public WebResponseVO playUrl(Integer roomId) {
        ErrorAssert.isNotNull(roomId, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(streamService.getPlayUrl(roomId));
    }

    /**
     * 获取录制回放列表
     */
    @PostMapping("/records")
    public WebResponseVO records(Integer roomId) {
        ErrorAssert.isNotNull(roomId, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(streamService.getRecordList(roomId));
    }
}
