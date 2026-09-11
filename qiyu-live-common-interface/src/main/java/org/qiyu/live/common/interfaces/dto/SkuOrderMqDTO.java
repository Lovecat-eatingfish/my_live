package org.qiyu.live.common.interfaces.dto;

import java.io.Serializable;

/**
 * SKU订单MQ消息DTO
 */
public class SkuOrderMqDTO implements Serializable {

    private Integer orderId;         // 订单ID
    private Long userId;             // 用户ID
    private Integer roomId;         // 直播间ID
    private String skuIdList;        // SKU ID列表（JSON格式）
    private Integer totalPrice;      // 订单总金额
    private Integer type;            // 消息类型：1-创建订单 2-支付成功 3-超时回滚

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
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

    public String getSkuIdList() {
        return skuIdList;
    }

    public void setSkuIdList(String skuIdList) {
        this.skuIdList = skuIdList;
    }

    public Integer getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Integer totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }
}
