package org.qiyu.live.video.provider.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 视频评论 PO
 */
@Data
@TableName("t_video_comment")
public class VideoCommentPO implements Serializable {

    private static final long serialVersionUID = 1547230900004L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long videoId;
    private Long userId;
    private String content;
    /** 状态（0删除 1正常） */
    private Integer status;
    private Date createTime;
}
