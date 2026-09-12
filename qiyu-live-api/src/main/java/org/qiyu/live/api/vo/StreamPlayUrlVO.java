package org.qiyu.live.api.vo;

/**
 * 播放地址响应VO
 */
public class StreamPlayUrlVO {

    /** 房间ID */
    private Integer roomId;

    /** 是否在直播 */
    private Boolean isLiving;

    /** HLS 播放地址 */
    private String hlsUrl;

    /** WebRTC 播放信令接口（同源相对路径） */
    private String rtcPlayApi;

    /** WebRTC 流地址 webrtc://host:port/live/streamKey */
    private String rtcStreamUrl;

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

    public Boolean getIsLiving() {
        return isLiving;
    }

    public void setIsLiving(Boolean isLiving) {
        this.isLiving = isLiving;
    }

    public String getHlsUrl() {
        return hlsUrl;
    }

    public void setHlsUrl(String hlsUrl) {
        this.hlsUrl = hlsUrl;
    }

    public String getRtcPlayApi() {
        return rtcPlayApi;
    }

    public void setRtcPlayApi(String rtcPlayApi) {
        this.rtcPlayApi = rtcPlayApi;
    }

    public String getRtcStreamUrl() {
        return rtcStreamUrl;
    }

    public void setRtcStreamUrl(String rtcStreamUrl) {
        this.rtcStreamUrl = rtcStreamUrl;
    }
}
