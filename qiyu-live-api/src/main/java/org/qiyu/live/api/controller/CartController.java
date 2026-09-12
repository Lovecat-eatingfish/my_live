package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.qiyu.live.api.service.ICartService;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 直播带货购物车控制器
 */
@RestController
@RequestMapping("/gift/cart")
public class CartController {

    @Resource
    private ICartService cartService;

    /**
     * 加入购物车
     */
    @PostMapping("/add")
    public WebResponseVO add(@RequestParam("roomId") Integer roomId,
                             @RequestParam("skuId") Integer skuId,
                             @RequestParam(value = "num", defaultValue = "1") Integer num) {
        cartService.addToCart(roomId, skuId, num);
        return WebResponseVO.success(cartService.cartDetail(roomId));
    }

    /**
     * 修改购物车商品数量
     */
    @PostMapping("/update")
    public WebResponseVO update(@RequestParam("roomId") Integer roomId,
                                @RequestParam("skuId") Integer skuId,
                                @RequestParam("num") Integer num) {
        cartService.updateCartNum(roomId, skuId, num);
        return WebResponseVO.success(cartService.cartDetail(roomId));
    }

    /**
     * 移除购物车商品
     */
    @PostMapping("/remove")
    public WebResponseVO remove(@RequestParam("roomId") Integer roomId,
                                @RequestParam("skuId") Integer skuId) {
        cartService.removeCartItem(roomId, skuId);
        return WebResponseVO.success(cartService.cartDetail(roomId));
    }

    /**
     * 清空购物车
     */
    @PostMapping("/clear")
    public WebResponseVO clear(@RequestParam("roomId") Integer roomId) {
        cartService.clearCart(roomId);
        return WebResponseVO.success(null);
    }

    /**
     * 查询购物车：返回商品列表 + 总件数 + 总金额
     */
    @PostMapping("/list")
    public WebResponseVO list(@RequestParam("roomId") Integer roomId) {
        return WebResponseVO.success(cartService.cartDetail(roomId));
    }

    /**
     * 购物车结算：下单并支付购物车全部商品
     */
    @PostMapping("/checkout")
    public WebResponseVO checkout(@RequestParam("roomId") Integer roomId) {
        return WebResponseVO.success(cartService.checkout(roomId));
    }
}
