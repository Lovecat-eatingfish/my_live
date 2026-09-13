package org.qiyu.live.living.provider.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * @Author linhao
 * @Date created in 9:07 下午 2023/1/2
 */
@TableName("t_living_room")
public class LivingRoomPO {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private Long anchorId;
    private Integer type;
    private String roomName;
    private String covertImg;
    private Integer status;
    /** SRS 推流状态（0未推流 1推流中，由 stream-provider 回调维护） */
    private Integer streamStatus;
    private Integer watchNum;
    private Integer goodNum;
    /** 付费类型（0免费 1门票） */
    private Integer payType;
    /** 门票价格（金币） */
    private Integer ticketPrice;
    private Integer recordEnabled;
    /** 直播间公告 */
    private String announcement;
        private Date startTime;
    private Date updateTime;

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

    public Integer getRecordEnabled() {
        return recordEnabled;
    }

    public void setRecordEnabled(Integer recordEnabled) {
        this.recordEnabled = recordEnabled;
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

    public Integer getStreamStatus() {
        return streamStatus;
    }

    public void setStreamStatus(Integer streamStatus) {
        this.streamStatus = streamStatus;
    }

    @Override
    public String toString() {
        return "LivingRoomPO{" + "id=" + id + ", anchorId=" + anchorId + ", type=" + type + ", roomName='" + roomName + '\'' + ", covertImg='" + covertImg +
                '\'' + ", status=" + status + ", watchNum=" + watchNum + ", goodNum=" + goodNum + ", startTime=" + startTime + ", updateTime=" + updateTime +
                '}';
    }

    public Integer getPayType() {
        return payType;
    }

    public void setPayType(Integer payType) {
        this.payType = payType;
    }

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
    }
    public String getAnnouncement() {
        return announcement;
    }

    public void setAnnouncement(String announcement) {
        this.announcement = announcement;
    }

}