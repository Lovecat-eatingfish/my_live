package org.qiyu.live.gift.provider.rpc;

import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.gift.dto.AnchorShopInfoDTO;
import org.qiyu.live.gift.interfaces.IAnchorShopRpc;
import org.qiyu.live.gift.provider.service.IAnchorShopService;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 带货主播权限RPC实现
 */
@DubboService
@Component
public class AnchorShopRpcImpl implements IAnchorShopRpc {

    @Resource
    private IAnchorShopService anchorShopService;

    @Override
    public List<AnchorShopInfoDTO> listByAnchorId(Long anchorId) {
        return anchorShopService.listByAnchorId(anchorId);
    }

    @Override
    public boolean updateShopStatus(Long anchorId, Integer skuId, Integer status) {
        return anchorShopService.updateShopStatus(anchorId, skuId, status);
    }
}
