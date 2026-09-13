package org.qiyu.live.common.interfaces.dto;

import java.io.Serializable;

/**
 * 敏感词管理 DTO（后台增删查用）
 */
public class RiskWordDTO implements Serializable {

    private Long id;
    private String word;
    /** 1拦截 2替换* 3仅记录 */
    private Integer level;
    /** 0全部 1弹幕 2昵称 3视频标题 4评论 5房间名 */
    private Integer scene;
    private Integer status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getScene() {
        return scene;
    }

    public void setScene(Integer scene) {
        this.scene = scene;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
