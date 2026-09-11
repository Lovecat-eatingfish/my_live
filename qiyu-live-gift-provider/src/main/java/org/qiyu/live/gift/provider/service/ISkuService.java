package org.qiyu.live.gift.provider.service;

import org.qiyu.live.gift.dto.SkuInfoDTO;

import java.util.List;

/**
 * 商品SKU Service接口
 */
public interface ISkuService {

    /**
     * 根据SKU ID查询
     */
    SkuInfoDTO getBySkuId(Integer skuId);

    /**
     * 根据类目查询商品列表
     */
    List<SkuInfoDTO> listByCategoryId(Integer categoryId);

    /**
     * 上架商品
     */
    void online(Integer skuId);

    /**
     * 下架商品
     */
    void offline(Integer skuId);

    /**
     * 新增商品
     */
    void add(SkuInfoDTO skuInfoDTO);
}
