package org.qiyu.live.common.interfaces.topic;

/**
 * @Author idea
 * @Date: Created in 16:14 2023/5/28
 * @Description
 */
public class GiftProviderTopicNames {

    /**
     * 移除礼物信息的缓存
     */
    public static final String REMOVE_GIFT_CACHE = "remove_gift_cache";

    /**
     * 发送礼物消息
     */
    public static final String SEND_GIFT = "send_gift";

    /**
     * 红包雨发送通知
     */
    public static final String RED_PACKET_RAIN_SEND = "red_packet_rain_send";

    /**
     * 红包雨用户领取通知
     */
    public static final String RED_PACKET_RAIN_RECEIVE = "red_packet_rain_receive";

    /**
     * SKU订单超时回滚
     */
    public static final String SKU_ORDER_TIMEOUT = "sku_order_timeout";

    /**
     * 订单状态变更通知
     */
    public static final String ORDER_STATUS_CHANGE = "order_status_change";
}
