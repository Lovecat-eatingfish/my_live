package org.qiyu.live.video.provider.rpc;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.video.dto.VideoCommentDTO;
import org.qiyu.live.video.dto.VideoDTO;
import org.qiyu.live.video.interfaces.rpc.IVideoRpc;
import org.qiyu.live.video.interfaces.rpc.TagDTO;
import org.qiyu.live.video.provider.service.IVideoService;

import java.util.List;

/**
 * 视频模块 RPC 实现
 */
@DubboService
public class VideoRpcImpl implements IVideoRpc {

    @Resource
    private IVideoService videoService;

    @Override
    public VideoDTO publish(VideoDTO videoDTO) {
        return videoService.publish(videoDTO);
    }

    @Override
    public PageWrapper<VideoDTO> listVideos(Integer tagId, Long viewerUserId, int page, int pageSize) {
        return videoService.listVideos(tagId, viewerUserId, page, pageSize);
    }

    @Override
    public PageWrapper<VideoDTO> searchVideos(String keyword, Long viewerUserId, int page, int pageSize) {
        return videoService.searchVideos(keyword, viewerUserId, page, pageSize);
    }

    @Override
    public PageWrapper<VideoDTO> feed(Long lastId, Long viewerUserId, int size) {
        return videoService.feed(lastId, viewerUserId, size);
    }

    @Override
    public void playReport(Long videoId, Long userId, int watchedSeconds, int duration) {
        videoService.playReport(videoId, userId, watchedSeconds, duration);
    }

    @Override
    public PageWrapper<VideoDTO> listRelated(Long videoId, Long viewerUserId, int size) {
        return videoService.listRelated(videoId, viewerUserId, size);
    }

    @Override
    public VideoDTO detail(Long videoId, Long viewerUserId) {
        return videoService.detail(videoId, viewerUserId);
    }

    @Override
    public boolean incPlayCount(Long videoId) {
        return videoService.incPlayCount(videoId);
    }

    @Override
    public boolean like(Long videoId, Long userId, boolean isLike) {
        return videoService.like(videoId, userId, isLike);
    }

    @Override
    public boolean favorite(Long videoId, Long userId, boolean isFavorite) {
        return videoService.favorite(videoId, userId, isFavorite);
    }

    @Override
    public boolean incShareCount(Long videoId) {
        return videoService.incShareCount(videoId);
    }

    @Override
    public VideoCommentDTO addComment(VideoCommentDTO commentDTO) {
        return videoService.addComment(commentDTO);
    }

    @Override
    public List<VideoCommentDTO> listComments(Long videoId, int page, int pageSize) {
        return videoService.listComments(videoId, page, pageSize);
    }

    @Override
    public boolean deleteComment(Long commentId, Long userId) {
        return videoService.deleteComment(commentId, userId);
    }

    @Override
    public List<TagDTO> listTags() {
        return videoService.listTags();
    }

    @Override
    public org.qiyu.live.common.interfaces.dto.PageWrapper<org.qiyu.live.video.dto.VideoDTO> adminListVideos(int page, int pageSize) {
        return videoService.adminListVideos(page, pageSize);

    }

    @Override
    public PageWrapper<VideoDTO> adminReviewList(int page, int pageSize) {
        return videoService.adminReviewList(page, pageSize);
    }

    @Override
    public boolean setVideoStatus(Long videoId, int status) {
        return videoService.setVideoStatus(videoId, status);
    }

    @Override
    public Integer addTag(String tagName) {
        return videoService.addTag(tagName);
    }

    @Override
    public boolean renameTag(Integer tagId, String tagName) {
        return videoService.renameTag(tagId, tagName);
    }

    @Override
    public boolean deleteTag(Integer tagId) {
        return videoService.deleteTag(tagId);
    }

    @Override
    public void recordHistory(Long userId, Long videoId) {
        videoService.recordHistory(userId, videoId);
    }

    @Override
    public org.qiyu.live.common.interfaces.dto.PageWrapper<org.qiyu.live.video.dto.VideoDTO> listHistory(Long userId, int page, int pageSize) {
        return videoService.listHistory(userId, page, pageSize);
    }

    @Override
    public org.qiyu.live.common.interfaces.dto.PageWrapper<org.qiyu.live.video.dto.VideoDTO> listByUser(Long userId, int page, int pageSize) {
        return videoService.listByUser(userId, page, pageSize);
    }

    @Override
    public org.qiyu.live.common.interfaces.dto.PageWrapper<org.qiyu.live.video.dto.VideoDTO> listByAction(Long userId, int actionType, int page, int pageSize) {
        return videoService.listByAction(userId, actionType, page, pageSize);
    }

    @Override
    public boolean retryTranscode(Long videoId) {
        return videoService.retryTranscode(videoId);
    }
}
