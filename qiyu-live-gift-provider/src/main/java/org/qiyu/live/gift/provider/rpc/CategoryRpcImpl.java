package org.qiyu.live.gift.provider.rpc;

import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.gift.dto.CategoryInfoDTO;
import org.qiyu.live.gift.interfaces.ICategoryRpc;
import org.qiyu.live.gift.provider.service.ICategoryService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 类目RPC实现
 */
@DubboService
@Component
public class CategoryRpcImpl implements ICategoryRpc {

    @Resource
    private ICategoryService categoryService;

    @Override
    public List<CategoryInfoDTO> listRootCategory() {
        return categoryService.listRootCategory();
    }

    @Override
    public List<CategoryInfoDTO> listByParentId(Integer parentId) {
        return categoryService.listByParentId(parentId);
    }
}
