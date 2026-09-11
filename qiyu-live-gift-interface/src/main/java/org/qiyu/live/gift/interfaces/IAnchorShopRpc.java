package org.qiyu.live.gift.interfaces;

import org.qiyu.live.gift.dto.AnchorShopInfoDTO;

import java.util.List;

/**
 * 带货主播权限RPC接口
 */
public interface IAnchorShopRpc {

    /**
     * 根据主播ID查询带货权限列表
     */
    List<AnchorShopInfoDTO> listByAnchorId(Long anchorId);
}
