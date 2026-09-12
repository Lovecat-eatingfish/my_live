package org.qiyu.live.video.provider.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 视频用户行为 PO（点赞/收藏）
 */
@Data
@TableName("t_video_user_action")
public class VideoUserActionPO implements Serializable {

    private static final long serialVersionUID = 1547230900003L;

    public static final int ACTION_LIKE = 1;
    public static final int ACTION_FAVORITE = 2;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    /** 1=点赞 2=收藏 */
    private Integer actionType;
    private Date createTime;
}
