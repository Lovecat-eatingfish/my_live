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
}
