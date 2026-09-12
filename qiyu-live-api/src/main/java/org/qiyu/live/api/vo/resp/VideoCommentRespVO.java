package org.qiyu.live.api.vo.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 视频评论响应 VO
 */
@Data
public class VideoCommentRespVO implements Serializable {

    private static final long serialVersionUID = 1547230910003L;

    private Long id;
    private Long videoId;
    private Long userId;
    private String nickName;
    private String avatar;
    private String content;
    private String createTime;
}
