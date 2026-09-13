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
    /** 转码状态（0处理中 1完成 2失败/未转码） */
    private Integer transcodeStatus;
    /** 审核状态（0下架 1上线 2审核中 3驳回） */
    private Integer status;
    /** 文件大小（字节，转码后修正） */
    private Long size;
    private String createTime;
}
