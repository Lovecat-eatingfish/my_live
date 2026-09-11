package org.idea.qiyu.live.framework.redis.starter.key;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;


/**
 * @Author idea
 * @Date: Created in 10:23 2023/6/20
 * @Description
 */
@Configuration
@Conditional(RedisKeyLoadMatch.class)
public class GiftProviderCacheKeyBuilder extends RedisKeyBuilder {

    private static String GIFT_CONFIG_CACHE = "gift_config_cache";
    private static String GIFT_LIST_CACHE = "gift_list_cache";
    private static String GIFT_CONSUME_KEY = "gift_consume_key";
    private static String GIFT_LIST_LOCK = "gift_list_lock";
    private static String LIVING_PK_KEY = "living_pk_key";
    private static String LIVING_PK_SEND_SEQ = "living_pk_send_seq";
    private static String LIVING_PK_IS_OVER = "living_pk_is over";
    private static String RED_PACKET_KEY = "red_packet_key";
    private static String RED_PACKET_RECEIVE_KEY = "red_packet_receive_key";
    private static String SKU_ORDER_KEY = "sku_order_key";
    private static String SKU_STOCK_LOCK = "sku_stock_lock";

    public String buildLivingPkIsOver(Integer roomId) {
        return super.getPrefix() + LIVING_PK_IS_OVER + super.getSplitItem() + roomId;
    }

    public String buildLivingPkSendSeq(Integer roomId) {
        return super.getPrefix() + LIVING_PK_SEND_SEQ + super.getSplitItem() + roomId;
    }

    public String buildLivingPkKey(Integer roomId) {
        return super.getPrefix() + LIVING_PK_KEY + super.getSplitItem() + roomId;
    }

    public String buildGiftConsumeKey(String uuid) {
        return super.getPrefix() + GIFT_CONSUME_KEY + super.getSplitItem() + uuid;
    }

    public String buildGiftConfigCacheKey(int giftId) {
        return super.getPrefix() + GIFT_CONFIG_CACHE + super.getSplitItem() + giftId;
    }

    public String buildGiftListCacheKey() {
        return super.getPrefix() + GIFT_LIST_CACHE;
    }

    public String buildGiftListLockCacheKey() {
        return super.getPrefix() + GIFT_LIST_LOCK;
    }

    // ========== 红包雨相关 ==========

    /**
     * 红包雨配置Key（用于缓存红包雨状态）
     */
    public String buildRedPacketKey(Integer redPacketId) {
        return super.getPrefix() + RED_PACKET_KEY + super.getSplitItem() + redPacketId;
    }

    /**
     * 红包领取记录Key（防止重复领取）
     * field: userId, value: 领取金额
     */
    public String buildRedPacketReceiveKey(Integer redPacketId) {
        return super.getPrefix() + RED_PACKET_RECEIVE_KEY + super.getSplitItem() + redPacketId;
    }

    // ========== SKU订单相关 ==========

    /**
     * SKU订单Key（用于订单超时处理）
     */
    public String buildSkuOrderKey(Integer orderId) {
        return super.getPrefix() + SKU_ORDER_KEY + super.getSplitItem() + orderId;
    }

    /**
     * SKU库存扣减分布式锁
     */
    public String buildSkuStockLockKey(Integer skuId) {
        return super.getPrefix() + SKU_STOCK_LOCK + super.getSplitItem() + skuId;
    }
}
