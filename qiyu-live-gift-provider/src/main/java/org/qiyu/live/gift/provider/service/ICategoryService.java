package org.qiyu.live.gift.provider.service;

import org.qiyu.live.gift.dto.CategoryInfoDTO;

import java.util.List;

/**
 * 类目Service接口
 */
public interface ICategoryService {

    /**
     * 查询所有一级类目
     */
    List<CategoryInfoDTO> listRootCategory();

    /**
     * 根据父ID查询子类目
     */
    List<CategoryInfoDTO> listByParentId(Integer parentId);

    /**
     * 根据ID查询类目
     */
    CategoryInfoDTO getById(Integer id);

    /**
     * 新增类目
     */
    void add(CategoryInfoDTO categoryInfoDTO);

    /**
     * 更新类目状态
     */
    void updateStatus(Integer id, Integer status);
}
