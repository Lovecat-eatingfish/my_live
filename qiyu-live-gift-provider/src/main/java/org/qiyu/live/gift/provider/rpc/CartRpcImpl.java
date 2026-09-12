package org.qiyu.live.gift.provider.rpc;

import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.gift.dto.CartItemDTO;
import org.qiyu.live.gift.interfaces.ICartRpc;
import org.qiyu.live.gift.provider.service.ICartService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 直播带货购物车RPC实现
 */
@DubboService
@Component
public class CartRpcImpl implements ICartRpc {

    @Resource
    private ICartService cartService;

    @Override
    public void addToCart(Long userId, Integer roomId, Integer skuId, Integer num) {
        cartService.addToCart(userId, roomId, skuId, num);
    }

    @Override
    public void updateCartNum(Long userId, Integer roomId, Integer skuId, Integer num) {
        cartService.updateCartNum(userId, roomId, skuId, num);
    }

    @Override
    public void removeCartItem(Long userId, Integer roomId, Integer skuId) {
        cartService.removeCartItem(userId, roomId, skuId);
    }

    @Override
    public void clearCart(Long userId, Integer roomId) {
        cartService.clearCart(userId, roomId);
    }

    @Override
    public List<CartItemDTO> listCart(Long userId, Integer roomId) {
        return cartService.listCart(userId, roomId);
    }

    @Override
    public Integer countCart(Long userId, Integer roomId) {
        return cartService.countCart(userId, roomId);
    }
}
