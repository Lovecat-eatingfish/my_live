package org.qiyu.live.common.interfaces.dto;

import java.io.Serializable;
import java.util.Date;

/**
 * 私信会话 DTO（peer 昵称/头像由 api 层经 IUserRpc 富化）
 */
public class DmConversationDTO implements Serializable {

    private Long peerUid;
    private String lastMsg;
    private Integer unreadCnt;
    private Date updateTime;

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
