package org.qiyu.live.msg.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 发送消息的内容
 *
 * @Author idea
 * @Date: Created in 15:00 2023/7/11
 * @Description
 */
public class MessageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = -8982006120358366161L;
    private Long userId;
    private Integer roomId;
    //发送人名称
    private String senderName;
    //发送人头像
    private String senderAvtar;
    //发送人等级（弹幕徽章渲染用，可能为空）
    private Integer level;
    /** 粉丝团灯牌等级（0=未加入；对应当前房间主播） */
    private Integer fanLevel;
    /**
     * 消息类型
     */
    private Integer type;
    /**
     * 消息内容
     */
    private String content;
    private Date createTime;
    private Date updateTime;

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getSenderAvtar() {
        return senderAvtar;
    }

    public void setSenderAvtar(String senderAvtar) {
        this.senderAvtar = senderAvtar;
    }

    @Override
    public String toString() {
        return "MessageDTO{" +
                "userId=" + userId +
                ", roomId=" + roomId +
                ", senderAvtar='" + senderAvtar + '\'' +
                ", type=" + type +
                ", content='" + content + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
    public Integer getFanLevel() {
        return fanLevel;
    }

    public void setFanLevel(Integer fanLevel) {
        this.fanLevel = fanLevel;
    }

}
