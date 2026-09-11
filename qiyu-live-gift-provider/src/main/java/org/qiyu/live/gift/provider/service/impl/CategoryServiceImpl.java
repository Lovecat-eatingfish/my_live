package org.qiyu.live.gift.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.gift.dto.CategoryInfoDTO;
import org.qiyu.live.gift.provider.dao.mapper.CategoryInfoMapper;
import org.qiyu.live.gift.provider.dao.po.CategoryInfoPO;
import org.qiyu.live.gift.provider.service.ICategoryService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 类目Service实现
 */
@Service
public class CategoryServiceImpl implements ICategoryService {

    @Resource
    private CategoryInfoMapper categoryInfoMapper;

    @Override
    public List<CategoryInfoDTO> listRootCategory() {
        LambdaQueryWrapper<CategoryInfoPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryInfoPO::getParentId, 0)
               .eq(CategoryInfoPO::getStatus, 1)
               .orderByAsc(CategoryInfoPO::getId);
        List<CategoryInfoPO> poList = categoryInfoMapper.selectList(wrapper);
        return ConvertBeanUtils.convertList(poList, CategoryInfoDTO.class);
    }

    @Override
    public List<CategoryInfoDTO> listByParentId(Integer parentId) {
        LambdaQueryWrapper<CategoryInfoPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryInfoPO::getParentId, parentId)
               .eq(CategoryInfoPO::getStatus, 1)
               .orderByAsc(CategoryInfoPO::getId);
        List<CategoryInfoPO> poList = categoryInfoMapper.selectList(wrapper);
        return ConvertBeanUtils.convertList(poList, CategoryInfoDTO.class);
    }

    @Override
    public CategoryInfoDTO getById(Integer id) {
        CategoryInfoPO po = categoryInfoMapper.selectById(id);
        return ConvertBeanUtils.convert(po, CategoryInfoDTO.class);
    }

    @Override
    public void add(CategoryInfoDTO categoryInfoDTO) {
        CategoryInfoPO po = ConvertBeanUtils.convert(categoryInfoDTO, CategoryInfoPO.class);
        categoryInfoMapper.insert(po);
    }

    @Override
    public void updateStatus(Integer id, Integer status) {
        CategoryInfoPO po = new CategoryInfoPO();
        po.setId(id);
        po.setStatus(status);
        categoryInfoMapper.updateById(po);
    }
}
