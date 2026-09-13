package org.qiyu.live.living.provider.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 直播间连麦记录
 */
@Data
@TableName("t_living_linkmic")
public class LivingLinkMicPO implements Serializable {

    private static final long serialVersionUID = 7230900001L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer roomId;
    private Long guestUserId;
    /** 0邀请中 1连麦中 2已结束 */
    private Integer status;
    private Date startTime;
    private Date endTime;
    private Date createTime;
}
