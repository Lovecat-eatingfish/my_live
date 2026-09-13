package org.qiyu.live.common.interfaces.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 开播推送 MQ 消息体（api 开播成功后发送，user-provider 消费推给粉丝）
 */
public class OpenLivingPushMqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long anchorId;
    private String anchorName;
    private Integer roomId;
    private String roomName;
    private String cover;

    public static OpenLivingPushMqDTO of(Long anchorId, String anchorName, Integer roomId, String roomName, String cover) {
        OpenLivingPushMqDTO dto = new OpenLivingPushMqDTO();
        dto.setAnchorId(anchorId);
        dto.setAnchorName(anchorName);
        dto.setRoomId(roomId);
        dto.setRoomName(roomName);
        dto.setCover(cover);
        return dto;
    }

    public Long getAnchorId() {
        return anchorId;
    }

    public void setAnchorId(Long anchorId) {
        this.anchorId = anchorId;
    }

    public String getAnchorName() {
        return anchorName;
    }

    public void setAnchorName(String anchorName) {
        this.anchorName = anchorName;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }
}
