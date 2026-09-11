package org.qiyu.live.gift.interfaces;

import org.qiyu.live.gift.dto.CategoryInfoDTO;

import java.util.List;

/**
 * 类目RPC接口
 */
public interface ICategoryRpc {

    /**
     * 查询所有一级类目
     */
    List<CategoryInfoDTO> listRootCategory();

    /**
     * 根据父ID查询子类目
     */
    List<CategoryInfoDTO> listByParentId(Integer parentId);
}
