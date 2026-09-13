package org.qiyu.live.msg.provider.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 私信会话（qiyu-live-msg 库，双方各一行）
 */
@TableName("t_user_dm_conversation")
public class UserDmConversationPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUid;
    private Long peerUid;
    private String lastMsg;
    private Integer unreadCnt;
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerUid() {
        return ownerUid;
    }

    public void setOwnerUid(Long ownerUid) {
        this.ownerUid = ownerUid;
    }

    public Long getPeerUid() {
        return peerUid;
    }

    public void setPeerUid(Long peerUid) {
        this.peerUid = peerUid;
    }

    public String getLastMsg() {
        return lastMsg;
    }

    public void setLastMsg(String lastMsg) {
        this.lastMsg = lastMsg;
    }

    public Integer getUnreadCnt() {
        return unreadCnt;
    }

    public void setUnreadCnt(Integer unreadCnt) {
        this.unreadCnt = unreadCnt;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
