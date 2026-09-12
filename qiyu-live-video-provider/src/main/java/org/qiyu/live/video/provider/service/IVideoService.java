package org.qiyu.live.video.provider.service;

import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.video.dto.VideoCommentDTO;
import org.qiyu.live.video.dto.VideoDTO;
import org.qiyu.live.video.interfaces.rpc.TagDTO;

import java.util.List;

/**
 * 视频服务接口
 */
public interface IVideoService {

    VideoDTO publish(VideoDTO videoDTO);

    PageWrapper<VideoDTO> listVideos(Integer tagId, Long viewerUserId, int page, int pageSize);

    VideoDTO detail(Long videoId, Long viewerUserId);

    boolean incPlayCount(Long videoId);

    boolean like(Long videoId, Long userId, boolean isLike);

    boolean favorite(Long videoId, Long userId, boolean isFavorite);

    boolean incShareCount(Long videoId);

    VideoCommentDTO addComment(VideoCommentDTO commentDTO);

    List<VideoCommentDTO> listComments(Long videoId, int page, int pageSize);

    boolean deleteComment(Long commentId, Long userId);

    List<TagDTO> listTags();
}
