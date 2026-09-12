package org.qiyu.live.gift.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.gift.dto.AnchorShopInfoDTO;
import org.qiyu.live.gift.provider.dao.mapper.AnchorShopInfoMapper;
import org.qiyu.live.gift.provider.dao.po.AnchorShopInfoPO;
import org.qiyu.live.gift.provider.service.IAnchorShopService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 带货主播权限Service实现
 */
@Service
public class AnchorShopServiceImpl implements IAnchorShopService {

    @Resource
    private AnchorShopInfoMapper anchorShopInfoMapper;

    @Override
    public List<AnchorShopInfoDTO> listByAnchorId(Long anchorId) {
        LambdaQueryWrapper<AnchorShopInfoPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnchorShopInfoPO::getAnchorId, anchorId)
               .eq(AnchorShopInfoPO::getStatus, 1);
        List<AnchorShopInfoPO> poList = anchorShopInfoMapper.selectList(wrapper);
        return ConvertBeanUtils.convertList(poList, AnchorShopInfoDTO.class);
    }

    @Override
    public void add(AnchorShopInfoDTO anchorShopInfoDTO) {
        AnchorShopInfoPO po = ConvertBeanUtils.convert(anchorShopInfoDTO, AnchorShopInfoPO.class);
        anchorShopInfoMapper.insert(po);
    }

    @Override
    public void updateStatus(Integer id, Integer status) {
        AnchorShopInfoPO po = new AnchorShopInfoPO();
        po.setId(id);
        po.setStatus(status);
        anchorShopInfoMapper.updateById(po);
    }

    @Override
    public boolean updateShopStatus(Long anchorId, Integer skuId, Integer status) {
        LambdaQueryWrapper<AnchorShopInfoPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnchorShopInfoPO::getAnchorId, anchorId)
               .eq(AnchorShopInfoPO::getSkuId, skuId);
        List<AnchorShopInfoPO> existList = anchorShopInfoMapper.selectList(wrapper);
        if (existList.isEmpty()) {
            if (status != null && status == 1) {
                AnchorShopInfoPO po = new AnchorShopInfoPO();
                po.setAnchorId(anchorId);
                po.setSkuId(skuId);
                po.setStatus(1);
                anchorShopInfoMapper.insert(po);
                return true;
            }
            // 本来就没上架，下架无操作
            return true;
        }
        AnchorShopInfoPO update = new AnchorShopInfoPO();
        update.setStatus(status);
        LambdaQueryWrapper<AnchorShopInfoPO> updateWrapper = new LambdaQueryWrapper<>();
        updateWrapper.eq(AnchorShopInfoPO::getAnchorId, anchorId)
                     .eq(AnchorShopInfoPO::getSkuId, skuId);
        anchorShopInfoMapper.update(update, updateWrapper);
        return true;
    }
}
