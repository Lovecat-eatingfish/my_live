package org.qiyu.live.stream.interfaces.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 播放地址响应DTO
 */
public class PlayBackDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** HLS 播放地址 */
    private String hlsUrl;

    /** HTTP-FLV 播放地址（预留） */
    private String httpFlvUrl;

    /** 是否在直播 */
    private Boolean isLiving;

    public String getHlsUrl() {
        return hlsUrl;
    }

    public void setHlsUrl(String hlsUrl) {
        this.hlsUrl = hlsUrl;
    }

    public String getHttpFlvUrl() {
        return httpFlvUrl;
    }

    public void setHttpFlvUrl(String httpFlvUrl) {
        this.httpFlvUrl = httpFlvUrl;
    }

    public Boolean getIsLiving() {
        return isLiving;
    }

    public void setIsLiving(Boolean isLiving) {
        this.isLiving = isLiving;
    }
}
