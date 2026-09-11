package org.qiyu.live.gift.interfaces;

import org.qiyu.live.gift.dto.SkuInfoDTO;

import java.util.List;

/**
 * 商品SKU RPC接口
 */
public interface ISkuRpc {

    /**
     * 根据SKU ID查询
     */
    SkuInfoDTO getBySkuId(Integer skuId);

    /**
     * 根据类目查询商品列表
     */
    List<SkuInfoDTO> listByCategoryId(Integer categoryId);
}
