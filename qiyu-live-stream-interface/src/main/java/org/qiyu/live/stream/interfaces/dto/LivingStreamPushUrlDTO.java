package org.qiyu.live.stream.interfaces.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 推流地址响应DTO
 */
@Data
public class LivingStreamPushUrlDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 完整推流地址 rtmp://host/live/streamKey */
    private String pushUrl;

    /** 流唯一标识 key */
    private String streamKey;

    /** 地址过期时间（预留） */
    private Long expireTime;

    /** WebRTC 推流信令接口（浏览器一键开播，SDP 交换用） */
    private String rtcPublishApi;

    /** WebRTC 流地址 webrtc://host:port/live/streamKey */
    private String rtcStreamUrl;
}
