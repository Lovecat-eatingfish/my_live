package org.qiyu.live.api.service;

import org.qiyu.live.gift.dto.CartItemDTO;

import java.util.List;
import java.util.Map;

/**
 * 直播带货购物车api服务
 */
public interface ICartService {

    void addToCart(Integer roomId, Integer skuId, Integer num);

    void updateCartNum(Integer roomId, Integer skuId, Integer num);

    void removeCartItem(Integer roomId, Integer skuId);

    void clearCart(Integer roomId);

    /**
     * 查询购物车详情：items(商品列表) + totalCount(总件数) + totalPrice(总金额)
     */
    Map<String, Object> cartDetail(Integer roomId);

    /**
     * 购物车结算：创建订单（预扣库存）→ 支付 → 清空购物车
     *
     * @return orderId / totalPrice / status
     */
    Map<String, Object> checkout(Integer roomId);
}
