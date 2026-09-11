package org.qiyu.live.gift.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.gift.dto.SkuStockInfoDTO;
import org.qiyu.live.gift.provider.dao.mapper.SkuStockInfoMapper;
import org.qiyu.live.gift.provider.dao.po.SkuStockInfoPO;
import org.qiyu.live.gift.provider.service.ISkuStockService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * SKU库存Service实现
 */
@Service
public class SkuStockServiceImpl implements ISkuStockService {

    @Resource
    private SkuStockInfoMapper skuStockInfoMapper;

    @Override
    public SkuStockInfoDTO getBySkuId(Integer skuId) {
        LambdaQueryWrapper<SkuStockInfoPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkuStockInfoPO::getSkuId, skuId);
        SkuStockInfoPO po = skuStockInfoMapper.selectOne(wrapper);
        return ConvertBeanUtils.convert(po, SkuStockInfoDTO.class);
    }

    @Override
    public SkuStockInfoPO getBySkuIdPO(Integer skuId) {
        LambdaQueryWrapper<SkuStockInfoPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkuStockInfoPO::getSkuId, skuId);
        return skuStockInfoMapper.selectOne(wrapper);
    }

    @Override
    public boolean decrementStock(Integer skuId, Integer num) {
        // 先查询当前库存版本
        SkuStockInfoPO stockPO = getBySkuIdPO(skuId);
        if (stockPO == null || stockPO.getStockNum() < num) {
            return false;
        }
        // 乐观锁扣减
        int result = skuStockInfoMapper.decrementStockWithVersion(skuId, num, stockPO.getVersion());
        return result > 0;
    }

    @Override
    public void increaseStock(Integer skuId, Integer num) {
        SkuStockInfoPO po = getBySkuIdPO(skuId);
        if (po == null) {
            po = new SkuStockInfoPO();
            po.setSkuId(skuId);
            po.setStockNum(num);
            po.setStatus(1);
            po.setVersion(0);
            skuStockInfoMapper.insert(po);
        } else {
            SkuStockInfoPO updatePO = new SkuStockInfoPO();
            updatePO.setId(po.getId());
            updatePO.setStockNum(po.getStockNum() + num);
            skuStockInfoMapper.updateById(updatePO);
        }
    }
}
