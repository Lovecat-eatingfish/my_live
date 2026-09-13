package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.qiyu.live.api.service.IVideoApiService;
import org.qiyu.live.api.vo.req.VideoPublishReqVO;
import org.qiyu.live.api.vo.resp.VideoCommentRespVO;
import org.qiyu.live.api.vo.resp.VideoDetailRespVO;
import org.qiyu.live.api.vo.resp.VideoItemRespVO;
import org.qiyu.live.api.vo.resp.VideoTagRespVO;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 视频模块控制器
 */
@RestController
@RequestMapping("/video")
public class VideoController {

    @Resource
    private IVideoApiService videoApiService;

    /** 上传视频文件（MP4/WebM/MOV，≤300MB），返回播放 URL */
    @PostMapping("/uploadVideo")
    public WebResponseVO uploadVideo(@RequestParam("file") MultipartFile file) {
        return WebResponseVO.success(videoApiService.uploadVideo(file, QiyuRequestContext.getUserId()));
    }

    /** 发布视频（元数据落库） */
    @PostMapping("/publish")
    public WebResponseVO publish(@RequestBody VideoPublishReqVO reqVO) {
        Long videoId = videoApiService.publish(reqVO, QiyuRequestContext.getUserId());
        return WebResponseVO.success(videoId);
    }

    /** 视频流列表（tagId=0 全部） */
    @PostMapping("/list")
    public WebResponseVO list(Integer tagId, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : pageSize;
        return WebResponseVO.success(videoApiService.listVideos(tagId == null ? 0 : tagId, p, ps));
    }

    /** 视频详情（播放量+1） */
    @PostMapping("/detail")
    public WebResponseVO detail(Long id) {
        return WebResponseVO.success(videoApiService.detail(id));
    }

    /** 点赞 / 取消点赞 */
    @PostMapping("/like")
    public WebResponseVO like(Long id, Boolean isLike) {
        return WebResponseVO.success(videoApiService.like(id, isLike == null || isLike));
    }

    /** 收藏 / 取消收藏 */
    @PostMapping("/favorite")
    public WebResponseVO favorite(Long id, Boolean isFavorite) {
        return WebResponseVO.success(videoApiService.favorite(id, isFavorite == null || isFavorite));
    }

    /** 分享计数 */
    @PostMapping("/share")
    public WebResponseVO share(Long id) {
        videoApiService.share(id);
        return WebResponseVO.success();
    }

    /** 标签列表 */
    @PostMapping("/tags")
    public WebResponseVO tags() {
        return WebResponseVO.success(videoApiService.listTags());
    }

    /** 评论分页 */
    @PostMapping("/comment/list")
    public WebResponseVO listComments(Long id, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : pageSize;
        List<VideoCommentRespVO> list = videoApiService.listComments(id, p, ps);
        return WebResponseVO.success(list);
    }

    /** 发表评论 */
    @PostMapping("/comment/add")
    public WebResponseVO addComment(Long id, String content) {
        return WebResponseVO.success(videoApiService.addComment(id, content));
    }

    /** 删除本人评论 */
    @PostMapping("/comment/delete")
    public WebResponseVO deleteComment(Long commentId) {
        return WebResponseVO.success(videoApiService.deleteComment(commentId));
    }

    /** 记录观看历史（播放≥3秒时前端上报） */
    @PostMapping("/history")
    public WebResponseVO recordHistory(Long id) {
        videoApiService.recordHistory(id);
        return WebResponseVO.success();
    }

    /** 我的观看历史 */
    @PostMapping("/my/history")
    public WebResponseVO myHistory(Integer page, Integer pageSize) {
        return WebResponseVO.success(videoApiService.listHistory(pg(page), ps(pageSize)));
    }

    /** 沉浸式 Feed（游标分页：lastId=上一页最后一条视频id，首页不传） */
    @PostMapping("/feed")
    public WebResponseVO feed(Long lastId, Integer size) {
        return WebResponseVO.success(videoApiService.feed(lastId, size == null || size < 1 || size > 20 ? 10 : size));
    }

    /** 相关推荐（同标签上架视频） */
    @PostMapping("/related")
    public WebResponseVO related(Long videoId, Integer size) {
        return WebResponseVO.success(videoApiService.related(videoId, size == null || size < 1 || size > 20 ? 6 : size));
    }

    /** 完播上报（ended 或离开时上报已观看秒数，≥90% 记完播） */
    @PostMapping("/playReport")
    public WebResponseVO playReport(Long videoId, Integer watchedSeconds, Integer duration) {
        videoApiService.playReport(videoId, watchedSeconds == null ? 0 : watchedSeconds,
                duration == null ? 0 : duration);
        return WebResponseVO.success();
    }

    /** 指定用户发布的视频（个人主页） */
    @PostMapping("/user/list")
    public WebResponseVO userList(Long targetUserId, Integer page, Integer pageSize) {
        return WebResponseVO.success(videoApiService.listByUser(targetUserId, pg(page), ps(pageSize)));
    }

    /** 我发布的视频 */
    @PostMapping("/my/list")
    public WebResponseVO myList(Integer page, Integer pageSize) {
        return WebResponseVO.success(videoApiService.listMyVideos(pg(page), ps(pageSize)));
    }

    /** 我收藏的视频 */
    @PostMapping("/my/favorites")
    public WebResponseVO myFavorites(Integer page, Integer pageSize) {
        return WebResponseVO.success(videoApiService.listMyFavorites(pg(page), ps(pageSize)));
    }

    /** 我点赞的视频 */
    @PostMapping("/my/likes")
    public WebResponseVO myLikes(Integer page, Integer pageSize) {
        return WebResponseVO.success(videoApiService.listMyLikes(pg(page), ps(pageSize)));
    }

    private int pg(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int ps(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
    }
}
