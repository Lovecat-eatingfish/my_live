package org.qiyu.live.api.service;

import org.qiyu.live.api.vo.req.VideoPublishReqVO;
import org.qiyu.live.api.vo.resp.VideoCommentRespVO;
import org.qiyu.live.api.vo.resp.VideoDetailRespVO;
import org.qiyu.live.api.vo.resp.VideoItemRespVO;
import org.qiyu.live.api.vo.resp.VideoTagRespVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 视频服务（api 层）
 */
public interface IVideoApiService {

    /** 上传视频文件到 MinIO，返回播放 URL */
    String uploadVideo(MultipartFile file, Long userId);

    /** 发布视频（元数据落库） */
    Long publish(VideoPublishReqVO reqVO, Long userId);

    /** 视频流列表 */
    List<VideoItemRespVO> listVideos(Integer tagId, int page, int pageSize);

    /** 视频详情（含播放量+1） */
    VideoDetailRespVO detail(Long videoId);

    /** 点赞/取消 */
    Boolean like(Long videoId, boolean isLike);

    /** 收藏/取消 */
    Boolean favorite(Long videoId, boolean isFavorite);

    /** 分享计数 */
    void share(Long videoId);

    /** 标签列表 */
    List<VideoTagRespVO> listTags();

    /** 评论分页 */
    List<VideoCommentRespVO> listComments(Long videoId, int page, int pageSize);

    /** 发表评论 */
    VideoCommentRespVO addComment(Long videoId, String content);

    /** 删除本人评论 */
    boolean deleteComment(Long commentId);
}
