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

    /**
     * 主播上架/下架商品（status: 1上架 0下架，写入 t_anchor_shop_info）
     */
    boolean updateShopStatus(Long anchorId, Integer skuId, Integer status);
}
