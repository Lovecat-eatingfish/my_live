package org.qiyu.live.stream.interfaces.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 推流地址响应DTO
 */
public class LivingStreamPushUrlDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 完整推流地址 rtmp://host/live/streamKey */
    private String pushUrl;

    /** 流唯一标识 key */
    private String streamKey;

    /** 地址过期时间（预留） */
    private Long expireTime;

    public String getPushUrl() {
        return pushUrl;
    }

    public void setPushUrl(String pushUrl) {
        this.pushUrl = pushUrl;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }
}
