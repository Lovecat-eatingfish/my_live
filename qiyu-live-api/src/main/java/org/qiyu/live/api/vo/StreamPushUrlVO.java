package org.qiyu.live.api.vo;

/**
 * 推流地址响应VO
 */
public class StreamPushUrlVO {

    /** 房间ID */
    private Integer roomId;

    /** 完整推流地址 rtmp://host:port/live/streamKey */
    private String pushUrl;

    /** 流唯一标识 key */
    private String streamKey;

    /** 地址过期时间（毫秒时间戳） */
    private Long expireTime;

    /** WebRTC 推流信令接口（浏览器一键开播，SDP 交换用） */
    private String rtcPublishApi;

    /** WebRTC 流地址 webrtc://host:port/live/streamKey */
    private String rtcStreamUrl;

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

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
    public String getRtcPublishApi() {
        return rtcPublishApi;
    }

    public void setRtcPublishApi(String rtcPublishApi) {
        this.rtcPublishApi = rtcPublishApi;
    }

    public String getRtcStreamUrl() {
        return rtcStreamUrl;
    }

    public void setRtcStreamUrl(String rtcStreamUrl) {
        this.rtcStreamUrl = rtcStreamUrl;
    }

}
