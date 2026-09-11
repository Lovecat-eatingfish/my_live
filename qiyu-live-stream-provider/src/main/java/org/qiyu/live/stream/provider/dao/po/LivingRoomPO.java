package org.qiyu.live.stream.provider.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 直播间PO（扩展自 living_provider 的 LivingRoomPO）
 * 注意：实际表为 t_living_room，此处列出流媒体相关扩展字段
 */
@TableName("t_living_room")
public class LivingRoomPO {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Long anchorId;
    private Integer type;
    private String roomName;
    private String covertImg;
    /** 房间状态：1=正常 */
    private Integer status;
    private Integer watchNum;
    private Integer goodNum;
    private Date startTime;
    private Date updateTime;

    /** ========== 流媒体扩展字段 ========== */

    /** 推流地址 */
    private String pushUrl;
    /** 流唯一标识 key */
    private String streamKey;
    /** 流状态：0=未开播 1=推流中 2=异常 */
    private Integer streamStatus;
    /** SRS 实例标识 */
    private String srsServerId;
    /** 本次开播时间 */
    private Date streamStartTime;
    /** 是否开启录制 */
    private Integer recordEnabled;

    // ===== getter/setter (仅新增字段) =====

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

    public Integer getStreamStatus() {
        return streamStatus;
    }

    public void setStreamStatus(Integer streamStatus) {
        this.streamStatus = streamStatus;
    }

    public String getSrsServerId() {
        return srsServerId;
    }

    public void setSrsServerId(String srsServerId) {
        this.srsServerId = srsServerId;
    }

    public Date getStreamStartTime() {
        return streamStartTime;
    }

    public void setStreamStartTime(Date streamStartTime) {
        this.streamStartTime = streamStartTime;
    }

    public Integer getRecordEnabled() {
        return recordEnabled;
    }

    public void setRecordEnabled(Integer recordEnabled) {
        this.recordEnabled = recordEnabled;
    }

    // ---- 以下为原有字段的 getter/setter ----

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Long getAnchorId() {
        return anchorId;
    }

    public void setAnchorId(Long anchorId) {
        this.anchorId = anchorId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getCovertImg() {
        return covertImg;
    }

    public void setCovertImg(String covertImg) {
        this.covertImg = covertImg;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getWatchNum() {
        return watchNum;
    }

    public void setWatchNum(Integer watchNum) {
        this.watchNum = watchNum;
    }

    public Integer getGoodNum() {
        return goodNum;
    }

    public void setGoodNum(Integer goodNum) {
        this.goodNum = goodNum;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
