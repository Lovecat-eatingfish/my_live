package org.qiyu.live.video.interfaces.rpc;

import org.qiyu.live.common.interfaces.dto.PageWrapper;
import org.qiyu.live.video.dto.VideoCommentDTO;
import org.qiyu.live.video.dto.VideoDTO;

import java.util.List;

/**
 * 视频模块 RPC 接口
 */
public interface IVideoRpc {

    /** 发布视频 */
    VideoDTO publish(VideoDTO videoDTO);

    /** 视频流列表（tagId=0 查全部；带作者信息与当前用户点赞/收藏状态） */
    PageWrapper<VideoDTO> listVideos(Integer tagId, Long viewerUserId, int page, int pageSize);

    /** 视频详情 */
    VideoDTO detail(Long videoId, Long viewerUserId);

    /** 播放量 +1 */
    boolean incPlayCount(Long videoId);

    /** 点赞 / 取消点赞，返回操作后是否处于点赞状态 */
    boolean like(Long videoId, Long userId, boolean isLike);

    /** 收藏 / 取消收藏，返回操作后是否处于收藏状态 */
    boolean favorite(Long videoId, Long userId, boolean isFavorite);

    /** 分享计数 +1 */
    boolean incShareCount(Long videoId);

    /** 发表评论 */
    VideoCommentDTO addComment(VideoCommentDTO commentDTO);

    /** 评论分页（按视频） */
    List<VideoCommentDTO> listComments(Long videoId, int page, int pageSize);

    /** 删除评论（本人才能删） */
    boolean deleteComment(Long commentId, Long userId);

    /** 标签列表 */
    List<TagDTO> listTags();

    /** 记录观看历史 */
    void recordHistory(Long userId, Long videoId);

    /** 我的观看历史 */
    PageWrapper<VideoDTO> listHistory(Long userId, int page, int pageSize);

    /** 我发布的视频 */
    PageWrapper<VideoDTO> listByUser(Long userId, int page, int pageSize);

    /** 我点赞/收藏的视频（actionType: 1赞 2藏） */
    PageWrapper<VideoDTO> listByAction(Long userId, int actionType, int page, int pageSize);
}
