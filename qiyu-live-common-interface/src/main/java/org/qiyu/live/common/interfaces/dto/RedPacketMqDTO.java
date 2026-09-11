package org.qiyu.live.common.interfaces.dto;

import java.io.Serializable;

/**
 * 红包雨MQ消息DTO
 */
public class RedPacketMqDTO implements Serializable {

    private Integer redPacketId;     // 红包配置ID
    private Long anchorId;           // 主播ID
    private Long userId;            // 用户ID（领取时使用）
    private Integer roomId;          // 直播间ID
    private String configCode;       // 红包配置码
    private Integer totalPrice;      // 红包总金额
    private Integer totalCount;      // 红包总数量
    private Integer maxGetPrice;     // 最大领取金额
    private Integer receivePrice;   // 领取金额（领取成功时使用）
    private Integer type;            // 消息类型：1-发送红包雨 2-领取红包

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

    public String getConfigCode() {
        return configCode;
    }

    public void setConfigCode(String configCode) {
        this.configCode = configCode;
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

    public Integer getReceivePrice() {
        return receivePrice;
    }

    public void setReceivePrice(Integer receivePrice) {
        this.receivePrice = receivePrice;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }
}
