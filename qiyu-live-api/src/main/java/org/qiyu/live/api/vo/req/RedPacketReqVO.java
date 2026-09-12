package org.qiyu.live.api.vo.req;

/**
 * 红包雨请求VO
 */
public class RedPacketReqVO {

    private Integer redPacketId;    // 红包配置ID
    private Integer roomId;         // 直播间ID
    private String configCode;      // 红包配置码（查询时使用）

    public Integer getRedPacketId() {
        return redPacketId;
    }

    public void setRedPacketId(Integer redPacketId) {
        this.redPacketId = redPacketId;
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

    private Integer totalPrice;     // 红包总金额（抖币）
    private Integer totalCount;     // 红包总数量
    private Integer maxGetPrice;    // 单个最大领取金额（抖币）

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
}
