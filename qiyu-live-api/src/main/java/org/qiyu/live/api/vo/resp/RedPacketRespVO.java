package org.qiyu.live.api.vo.resp;

import java.util.Date;

/**
 * 红包雨响应VO
 */
public class RedPacketRespVO {

    private Integer redPacketId;
    private Long anchorId;
    private Integer roomId;
    private Integer totalPrice;      // 红包总金额
    private Integer totalCount;      // 红包总数量
    private Integer maxGetPrice;     // 最大领取金额
    private Integer status;          // 1-待预热 2-已预热 3-已发送 4-已结算
    private Integer receivePrice;    // 领取金额（领取成功后返回）
    private String configCode;       // 红包配置码

    public Integer getRedPacketId() {
        return redPacketId;
    }

    public void setRedPacketId(Integer redPacketId) {
        this.redPacketId = redPacketId;
    }

    public Long getAnchorId() {
        return anchorId;
    }

    public void setAnchorId(Long anchorId) {
        this.anchorId = anchorId;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

    public Integer getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Integer totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getMaxGetPrice() {
        return maxGetPrice;
    }

    public void setMaxGetPrice(Integer maxGetPrice) {
        this.maxGetPrice = maxGetPrice;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getReceivePrice() {
        return receivePrice;
    }

    public void setReceivePrice(Integer receivePrice) {
        this.receivePrice = receivePrice;
    }

    public String getConfigCode() {
        return configCode;
    }

    public void setConfigCode(String configCode) {
        this.configCode = configCode;
    }
}
