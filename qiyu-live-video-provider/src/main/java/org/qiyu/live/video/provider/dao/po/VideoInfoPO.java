package org.qiyu.live.video.provider.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 视频信息 PO
 */
@Data
@TableName("t_video_info")
public class VideoInfoPO implements Serializable {

    private static final long serialVersionUID = 1547230900001L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String videoUrl;
    private String coverUrl;
    private Integer tagId;
    /** 时长（秒） */
    private Integer duration;
    /** 文件大小（字节） */
    private Long size;
    private Long playCount;
    private Long likeCount;
    private Long favoriteCount;
    private Long shareCount;
    private Long commentCount;
    /** 状态（0下架 1上线） */
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
