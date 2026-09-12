package org.qiyu.live.gift.interfaces;

import org.qiyu.live.gift.dto.SkuOrderInfoDTO;

/**
 * 商品订单RPC接口
 */
public interface ISkuOrderRpc {

    /**
     * 创建订单
     */
    SkuOrderInfoDTO createOrder(SkuOrderInfoDTO orderInfoDTO);

    /**
     * 支付成功
     */
    boolean paySuccess(Integer orderId);

    /**
     * 根据ID查询
     */
    SkuOrderInfoDTO getById(Integer id);

    /**
     * 根据用户ID查询订单列表
     */
    java.util.List<SkuOrderInfoDTO> listByUserId(Long userId);
}
