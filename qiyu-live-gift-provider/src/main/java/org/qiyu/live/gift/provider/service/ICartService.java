package org.qiyu.live.gift.provider.service;

import org.qiyu.live.gift.dto.CartItemDTO;

import java.util.List;

/**
 * 直播带货购物车Service
 */
public interface ICartService {

    void addToCart(Long userId, Integer roomId, Integer skuId, Integer num);

    void updateCartNum(Long userId, Integer roomId, Integer skuId, Integer num);

    void removeCartItem(Long userId, Integer roomId, Integer skuId);

    void clearCart(Long userId, Integer roomId);

    List<CartItemDTO> listCart(Long userId, Integer roomId);

    Integer countCart(Long userId, Integer roomId);
}
