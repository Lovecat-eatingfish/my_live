package org.qiyu.live.admin.controller;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.qiyu.live.video.dto.VideoDTO;
import org.qiyu.live.video.interfaces.rpc.IVideoRpc;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 内容管理：视频列表 / 上架下架
 */
@RestController
@RequestMapping("/video")
public class AdminVideoController {

    @DubboReference(check = false)
    private IVideoRpc videoRpc;

    @PostMapping("/list")
    public WebResponseVO list(Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        PageWrapper<VideoDTO> wrapper = videoRpc.adminListVideos(p, ps);
        return WebResponseVO.success(Map.of("list", wrapper.getList(), "hasNext", wrapper.isHasNext()));
    }

    /** 上架/下架（status: 1上架 0下架） */
    @PostMapping("/setStatus")
    public WebResponseVO setStatus(Long id, Integer status) {
        ErrorAssert.isNotNull(id, BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isNotNull(status, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(videoRpc.setVideoStatus(id, status));
    }

    /** 转码重试：未转码/失败的视频重新投递转码任务 */
    @PostMapping("/transcodeRetry")
    public WebResponseVO transcodeRetry(Long id) {
        ErrorAssert.isNotNull(id, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(videoRpc.retryTranscode(id));
    }

    /** 审核队列：status=2 审核中的视频，按提交时间正序 */
    @PostMapping("/reviewList")
    public WebResponseVO reviewList(Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        PageWrapper<VideoDTO> wrapper = videoRpc.adminReviewList(p, ps);
        return WebResponseVO.success(Map.of("list", wrapper.getList(), "hasNext", wrapper.isHasNext()));
    }

    /** 审核（pass=true 置 1 上线；false 置 3 驳回） */
    @PostMapping("/review")
    public WebResponseVO review(Long id, Boolean pass) {
        ErrorAssert.isNotNull(id, BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isNotNull(pass, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(videoRpc.setVideoStatus(id, Boolean.TRUE.equals(pass) ? 1 : 3));
    }
}
