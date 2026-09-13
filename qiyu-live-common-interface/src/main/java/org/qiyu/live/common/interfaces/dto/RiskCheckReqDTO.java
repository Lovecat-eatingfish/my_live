package org.qiyu.live.common.interfaces.dto;

import java.io.Serializable;

/**
 * 敏感词检测请求
 */
public class RiskCheckReqDTO implements Serializable {

    private String text;
    /** 场景：见 RiskConstants.SCENE_* */
    private int scene;
    private Long userId;

    public static RiskCheckReqDTO of(String text, int scene, Long userId) {
        RiskCheckReqDTO dto = new RiskCheckReqDTO();
        dto.setText(text);
        dto.setScene(scene);
        dto.setUserId(userId);
        return dto;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getScene() {
        return scene;
    }

    public void setScene(int scene) {
        this.scene = scene;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
