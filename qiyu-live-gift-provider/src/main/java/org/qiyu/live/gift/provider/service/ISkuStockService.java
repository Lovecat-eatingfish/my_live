package org.qiyu.live.gift.provider.service;

import org.qiyu.live.gift.dto.SkuStockInfoDTO;
import org.qiyu.live.gift.provider.dao.po.SkuStockInfoPO;

/**
 * SKU库存Service接口
 */
public interface ISkuStockService {

    /**
     * 根据SKU ID查询库存
     */
    SkuStockInfoDTO getBySkuId(Integer skuId);

    /**
     * 根据SKU ID查询库存（返回PO）
     */
    SkuStockInfoPO getBySkuIdPO(Integer skuId);

    /**
     * 扣减库存（乐观锁）
     * @return true扣减成功，false库存不足
     */
    boolean decrementStock(Integer skuId, Integer num);

    /**
     * 增加库存
     */
    void increaseStock(Integer skuId, Integer num);
}
