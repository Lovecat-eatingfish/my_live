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

    /** 记录观看历史（uk: user+video，重复观看刷时间） */
    void recordHistory(Long userId, Long videoId);

    /** 我的观看历史 */
    PageWrapper<VideoDTO> listHistory(Long userId, int page, int pageSize);

    /** 我发布的视频 */
    PageWrapper<VideoDTO> listByUser(Long userId, int page, int pageSize);

    /** 我点赞/收藏的视频（actionType: 1赞 2藏） */
    PageWrapper<VideoDTO> listByAction(Long userId, int actionType, int page, int pageSize);
}
