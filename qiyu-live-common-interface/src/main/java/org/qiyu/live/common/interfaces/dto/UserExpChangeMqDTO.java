package org.qiyu.live.common.interfaces.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户经验值变更 MQ 消息体（看播/弹幕/送礼/发视频 各动作方发送，user-provider 单点消费）
 */
public class UserExpChangeMqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    /** 经验增量 */
    private Long expDelta;
    /** 场景：UserLevelConstants.EXP_SCENE_* */
    private Integer scene;
    /** 动作发生所在直播间（升级时用于房间广播 5570，可为空） */
    private Integer roomId;

    public static UserExpChangeMqDTO of(Long userId, long expDelta, int scene, Integer roomId) {
        UserExpChangeMqDTO dto = new UserExpChangeMqDTO();
        dto.setUserId(userId);
        dto.setExpDelta(expDelta);
        dto.setScene(scene);
        dto.setRoomId(roomId);
        return dto;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getExpDelta() {
        return expDelta;
    }

    public void setExpDelta(Long expDelta) {
        this.expDelta = expDelta;
    }

    public Integer getScene() {
        return scene;
    }

    public void setScene(Integer scene) {
        this.scene = scene;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }
}
