package org.qiyu.live.api.vo;

/**
 * 流状态响应VO
 */
public class StreamStatusVO {

    /** 房间ID */
    private Integer roomId;

    /** 流状态: 0=未开播 1=推流中 2=异常 */
    private Integer status;

    /** 当前观看人数 */
    private Integer viewerCount;

    /** 推流码率(kbps) */
    private Integer bitrate;

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getViewerCount() {
        return viewerCount;
    }

    public void setViewerCount(Integer viewerCount) {
        this.viewerCount = viewerCount;
    }

    public Integer getBitrate() {
        return bitrate;
    }

    public void setBitrate(Integer bitrate) {
        this.bitrate = bitrate;
    }
}
