package org.qiyu.live.api.service.impl;

import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.api.service.ICartService;
import org.qiyu.live.api.service.IGiftService;
import org.qiyu.live.api.vo.req.SkuOrderReqVO;
import org.qiyu.live.api.vo.resp.SkuOrderRespVO;
import org.qiyu.live.gift.dto.CartItemDTO;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.qiyu.live.gift.interfaces.ICartRpc;
import org.qiyu.live.api.error.ApiErrorEnum;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 直播带货购物车api服务实现
 * 结算链路：购物车skuIdList展开（数量重复展开）→ 预下单扣库存 → 支付扣余额 → 清空购物车
 */
@Service
public class CartServiceImpl implements ICartService {

    @DubboReference(check = false)
    private ICartRpc cartRpc;
    @Resource
    private IGiftService giftService;

    @Override
    public void addToCart(Integer roomId, Integer skuId, Integer num) {
        cartRpc.addToCart(currentUserId(), roomId, skuId, num);
    }

    @Override
    public void updateCartNum(Integer roomId, Integer skuId, Integer num) {
        cartRpc.updateCartNum(currentUserId(), roomId, skuId, num);
    }

    @Override
    public void removeCartItem(Integer roomId, Integer skuId) {
        cartRpc.removeCartItem(currentUserId(), roomId, skuId);
    }

    @Override
    public void clearCart(Integer roomId) {
        cartRpc.clearCart(currentUserId(), roomId);
    }

    @Override
    public Map<String, Object> cartDetail(Integer roomId) {
        List<CartItemDTO> items = cartRpc.listCart(currentUserId(), roomId);
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        int totalCount = 0;
        int totalPrice = 0;
        if (!CollectionUtils.isEmpty(items)) {
            for (CartItemDTO item : items) {
                totalCount += item.getNum();
                totalPrice += item.getNum() * item.getSkuPrice();
            }
        }
        result.put("totalCount", totalCount);
        result.put("totalPrice", totalPrice);
        return result;
    }

    @Override
    public Map<String, Object> checkout(Integer roomId) {
        Long userId = currentUserId();
        List<CartItemDTO> items = cartRpc.listCart(userId, roomId);
        if (CollectionUtils.isEmpty(items)) {
            ErrorAssert.isTure(false, ApiErrorEnum.PARAM_ERROR);
        }
        //按数量展开skuIdList（订单模型按id逐个扣库存和计价）
        List<String> skuIdStrList = new ArrayList<>();
        int expectTotalPrice = 0;
        for (CartItemDTO item : items) {
            for (int i = 0; i < item.getNum(); i++) {
                skuIdStrList.add(String.valueOf(item.getSkuId()));
            }
            expectTotalPrice += item.getNum() * item.getSkuPrice();
        }
        SkuOrderReqVO orderReqVO = new SkuOrderReqVO();
        orderReqVO.setRoomId(roomId);
        orderReqVO.setSkuIdList(String.join(",", skuIdStrList));
        //预下单：扣库存+创建订单+发送超时回滚延迟消息
        SkuOrderRespVO orderVO = giftService.createSkuOrder(orderReqVO);
        //支付订单：扣余额+修改订单状态
        SkuOrderReqVO payReqVO = new SkuOrderReqVO();
        payReqVO.setOrderId(orderVO.getOrderId());
        boolean paySuccess = giftService.paySuccessSkuOrder(payReqVO);
        if (!paySuccess) {
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", orderVO.getOrderId());
            result.put("totalPrice", orderVO.getTotalPrice());
            result.put("status", orderVO.getStatus());
            result.put("paySuccess", false);
            return result;
        }
        //支付成功清空购物车
        cartRpc.clearCart(userId, roomId);
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderVO.getOrderId());
        result.put("totalPrice", orderVO.getTotalPrice());
        result.put("status", 1);
        result.put("paySuccess", true);
        return result;
    }

    private Long currentUserId() {
        return QiyuRequestContext.getUserId();
    }
}
