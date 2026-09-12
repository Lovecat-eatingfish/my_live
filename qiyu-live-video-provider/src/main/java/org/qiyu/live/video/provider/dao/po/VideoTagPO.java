package org.qiyu.live.video.provider.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 视频标签 PO
 */
@Data
@TableName("t_video_tag")
public class VideoTagPO implements Serializable {

    private static final long serialVersionUID = 1547230900002L;

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String tagName;
    private Integer sort;
    private Integer status;
}
