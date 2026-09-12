package org.qiyu.live.api.vo.resp;

import java.util.Date;

/**
 * 商品订单响应VO
 */
public class SkuOrderRespVO {

    private Integer orderId;
    private String skuIdList;
    private Long userId;
    private Integer roomId;
    private Integer totalPrice;
    private Integer status;          // 0-待支付 1-已支付 2-已取消
    private String statusDesc;
    private Date createTime;

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public String getSkuIdList() {
        return skuIdList;
    }

    public void setSkuIdList(String skuIdList) {
        this.skuIdList = skuIdList;
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

    public Integer getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(Integer totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusDesc() {
        return statusDesc;
    }

    public void setStatusDesc(String statusDesc) {
        this.statusDesc = statusDesc;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
