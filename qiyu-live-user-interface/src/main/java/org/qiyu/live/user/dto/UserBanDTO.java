package org.qiyu.live.user.dto;

import java.io.Serializable;

/**
 * 账号封禁/禁言 DTO
 */
public class UserBanDTO implements Serializable {

    /** 禁言 */
    public static final int TYPE_MUTE = 1;
    /** 封号 */
    public static final int TYPE_ACCOUNT_BAN = 2;

    private Long userId;
    /** 1禁言 2封号 */
    private Integer type;
    /** 生效时长（分钟），0 或 null = 永久 */
    private Integer durationMinutes;
    private String reason;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
