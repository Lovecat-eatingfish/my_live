package org.qiyu.live.gift.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.gift.dto.SkuInfoDTO;
import org.qiyu.live.gift.provider.dao.mapper.SkuInfoMapper;
import org.qiyu.live.gift.provider.dao.po.SkuInfoPO;
import org.qiyu.live.gift.provider.service.ISkuService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 商品SKU Service实现
 */
@Service
public class SkuServiceImpl implements ISkuService {

    @Resource
    private SkuInfoMapper skuInfoMapper;

    @Override
    public SkuInfoDTO getBySkuId(Integer skuId) {
        LambdaQueryWrapper<SkuInfoPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkuInfoPO::getSkuId, skuId).eq(SkuInfoPO::getStatus, 1);
        SkuInfoPO po = skuInfoMapper.selectOne(wrapper);
        return ConvertBeanUtils.convert(po, SkuInfoDTO.class);
    }

    @Override
    public List<SkuInfoDTO> listByCategoryId(Integer categoryId) {
        LambdaQueryWrapper<SkuInfoPO> wrapper = new LambdaQueryWrapper<>();
        // categoryId 为空时不筛选分类，返回全部在架商品（主播商品管理用）
        if (categoryId != null) {
            wrapper.eq(SkuInfoPO::getCategoryId, categoryId);
        }
        wrapper.eq(SkuInfoPO::getStatus, 1)
               .orderByDesc(SkuInfoPO::getId);
        List<SkuInfoPO> poList = skuInfoMapper.selectList(wrapper);
        return ConvertBeanUtils.convertList(poList, SkuInfoDTO.class);
    }

    @Override
    public void online(Integer skuId) {
        SkuInfoPO po = new SkuInfoPO();
        po.setSkuId(skuId);
        po.setStatus(1);
        skuInfoMapper.updateById(po);
    }

    @Override
    public void offline(Integer skuId) {
        SkuInfoPO po = new SkuInfoPO();
        po.setSkuId(skuId);
        po.setStatus(0);
        skuInfoMapper.updateById(po);
    }

    @Override
    public void add(SkuInfoDTO skuInfoDTO) {
        SkuInfoPO po = ConvertBeanUtils.convert(skuInfoDTO, SkuInfoPO.class);
        skuInfoMapper.insert(po);
    }
}
