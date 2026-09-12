package org.qiyu.live.api.vo.req;

import lombok.Data;

/**
 * 发布视频请求 VO
 */
@Data
public class VideoPublishReqVO {

    private String title;
    private String description;
    private Integer tagId;
    /** 已上传的视频播放地址（uploadVideo 返回） */
    private String videoUrl;
    /** 已上传的封面地址（uploadCover 返回，可空） */
    private String coverUrl;
    /** 时长（秒） */
    private Integer duration;
    /** 文件大小（字节） */
    private Long size;
}
