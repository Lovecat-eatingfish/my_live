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

    /** 按标题模糊搜索上架视频（搜索中心用） */
    PageWrapper<VideoDTO> searchVideos(String keyword, Long viewerUserId, int page, int pageSize);

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

    /** 相关推荐：同标签的上架视频（排除自身，id 倒序） */
    PageWrapper<VideoDTO> listRelated(Long videoId, Long viewerUserId, int size);

    /** 沉浸式 Feed 游标分页：热度分(play*0.4+like*0.3)排序，lastId=上一页最后一条视频id（首页传 null） */
    PageWrapper<VideoDTO> feed(Long lastId, Long viewerUserId, int size);

    /** 管理端：转码重试（重新投递转码 MQ） */
    boolean retryTranscode(Long videoId);

    /** 完播上报：写 t_video_play_log，观看≥90% 记完播 */
    void playReport(Long videoId, Long userId, int watchedSeconds, int duration);

    /** 我发布的视频 */
    PageWrapper<VideoDTO> listByUser(Long userId, int page, int pageSize);

    /** 我点赞/收藏的视频（actionType: 1赞 2藏） */
    PageWrapper<VideoDTO> listByAction(Long userId, int actionType, int page, int pageSize);

    // ==================== 管理端 ====================

    /** 管理端视频列表（含未上架，adminView=true 时不过滤状态） */
    PageWrapper<VideoDTO> adminListVideos(int page, int pageSize);

    /** 审核队列：status=2 审核中的视频，按提交时间正序 */
    PageWrapper<VideoDTO> adminReviewList(int page, int pageSize);

    /** 上架/下架视频（status: 1上架 0下架） */
    boolean setVideoStatus(Long videoId, int status);

    /** 新增标签，返回标签id */
    Integer addTag(String tagName);

    /** 重命名标签 */
    boolean renameTag(Integer tagId, String tagName);

    /** 删除标签 */
    boolean deleteTag(Integer tagId);
}
