package org.qiyu.live.gift.interfaces;

import org.qiyu.live.gift.dto.CartItemDTO;

import java.util.List;

/**
 * 直播带货购物车RPC接口
 * 购物车以直播间为维度存储在redis hash中，直播结束购物车数据随之过期
 */
public interface ICartRpc {

    /**
     * 加入购物车（已存在则数量累加）
     *
     * @param userId 用户ID
     * @param roomId 直播间ID
     * @param skuId  商品ID
     * @param num    数量
     */
    void addToCart(Long userId, Integer roomId, Integer skuId, Integer num);

    /**
     * 修改购物车中商品数量（设置为指定值，数量<=0则移除）
     */
    void updateCartNum(Long userId, Integer roomId, Integer skuId, Integer num);

    /**
     * 移除购物车商品
     */
    void removeCartItem(Long userId, Integer roomId, Integer skuId);

    /**
     * 清空购物车
     */
    void clearCart(Long userId, Integer roomId);

    /**
     * 查询购物车（组装商品详情）
     */
    List<CartItemDTO> listCart(Long userId, Integer roomId);

    /**
     * 查询购物车中商品件数
     */
    Integer countCart(Long userId, Integer roomId);
}
