package org.qiyu.live.stream.interfaces.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 播放地址响应DTO
 */
@Data
public class PlayBackDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** HLS 播放地址 */
    private String hlsUrl;

    /** HTTP-FLV 播放地址（预留） */
    private String httpFlvUrl;

    /** 是否在直播 */
    private Boolean isLiving;

    private Integer roomId;

    /** WebRTC 播放信令接口（同源相对路径，经代理转发到 SRS） */
    private String rtcPlayApi;

    /** WebRTC 流地址 webrtc://host:port/live/streamKey */
    private String rtcStreamUrl;


}
