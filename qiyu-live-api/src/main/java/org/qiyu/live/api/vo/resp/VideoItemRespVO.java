package org.qiyu.live.api.vo.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 视频卡片/详情响应 VO
 */
@Data
public class VideoItemRespVO implements Serializable {

    private static final long serialVersionUID = 1547230910001L;

    private Long id;
    private Long userId;
    private String nickName;
    private String avatar;
    private String title;
    private String description;
    private String videoUrl;
    private String coverUrl;
    private Integer tagId;
    private String tagName;
    private Integer duration;
    private Long playCount;
    private Long likeCount;
    private Long favoriteCount;
    private Long shareCount;
    private Long commentCount;
    private Boolean liked;
    private Boolean favorited;
    private String createTime;
}
