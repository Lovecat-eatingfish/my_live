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
    /** 转码状态（0处理中 1完成 2失败/未转码，失败回退播原 video_url） */
    private Integer transcodeStatus;

    /** 热度分冗余列：play_count*0.4 + like_count*0.3，feed 排序/游标用（表达式排序无法走索引） */
    private Double heatScore;
    private Date createTime;
    private Date updateTime;
}
