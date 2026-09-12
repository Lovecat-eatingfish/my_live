package org.qiyu.live.gift.provider.rpc;

import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.gift.dto.SkuOrderInfoDTO;
import org.qiyu.live.gift.interfaces.ISkuOrderRpc;
import org.qiyu.live.gift.provider.service.ISkuOrderService;
import jakarta.annotation.Resource;

/**
 * 商品订单RPC实现
 */
@DubboService
public class SkuOrderRpcImpl implements ISkuOrderRpc {

    @Resource
    private ISkuOrderService skuOrderService;

    @Override
    public SkuOrderInfoDTO createOrder(SkuOrderInfoDTO orderInfoDTO) {
        return skuOrderService.createOrder(orderInfoDTO);
    }

    @Override
    public boolean paySuccess(Integer orderId) {
        return skuOrderService.paySuccess(orderId);
    }

    @Override
    public SkuOrderInfoDTO getById(Integer id) {
        return skuOrderService.getById(id);
    }

    @Override
    public java.util.List<SkuOrderInfoDTO> listByUserId(Long userId) {
        return skuOrderService.listByUserId(userId);
    }
}
