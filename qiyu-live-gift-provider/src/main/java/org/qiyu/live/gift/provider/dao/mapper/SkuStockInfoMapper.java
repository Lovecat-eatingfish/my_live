package org.qiyu.live.gift.provider.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.qiyu.live.gift.provider.dao.po.SkuStockInfoPO;

/**
 * SKU库存Mapper
 */
@Mapper
public interface SkuStockInfoMapper extends BaseMapper<SkuStockInfoPO> {

    /**
     * 乐观锁扣减库存
     */
    @Update("UPDATE t_sku_stock_info SET stock_num = stock_num - #{num}, version = version + 1 WHERE sku_id = #{skuId} AND stock_num >= #{num} AND version = #{version}")
    int decrementStockWithVersion(@Param("skuId") Integer skuId, @Param("num") Integer num, @Param("version") Integer version);
}
