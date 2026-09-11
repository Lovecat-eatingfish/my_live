package org.qiyu.live.gift.provider.rpc;

import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.gift.dto.SkuInfoDTO;
import org.qiyu.live.gift.interfaces.ISkuRpc;
import org.qiyu.live.gift.provider.service.ISkuService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 商品SKU RPC实现
 */
@DubboService
@Component
public class SkuRpcImpl implements ISkuRpc {

    @Resource
    private ISkuService skuService;

    @Override
    public SkuInfoDTO getBySkuId(Integer skuId) {
        return skuService.getBySkuId(skuId);
    }

    @Override
    public List<SkuInfoDTO> listByCategoryId(Integer categoryId) {
        return skuService.listByCategoryId(categoryId);
    }
}
