package org.qiyu.live.video.provider.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 视频播放完播上报
 */
@Data
@TableName("t_video_play_log")
public class VideoPlayLogPO implements Serializable {

    private static final long serialVersionUID = 1547230900002L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long videoId;
    private Long userId;
    private Integer watchedSeconds;
    private Integer duration;
    private Integer isComplete;
    private Date createTime;
}
