package org.qiyu.live.api.vo.req;

/**
 * 商品订单请求VO
 */
public class SkuOrderReqVO {

    private Integer orderId;           // 订单ID（查询/支付时使用）
    private String skuIdList;           // SKU ID列表，逗号分隔
    private Integer roomId;            // 直播间ID

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

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }
}
