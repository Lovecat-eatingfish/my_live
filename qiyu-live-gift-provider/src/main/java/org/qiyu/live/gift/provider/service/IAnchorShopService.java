package org.qiyu.live.gift.provider.service;

import org.qiyu.live.gift.dto.AnchorShopInfoDTO;

import java.util.List;

/**
 * 带货主播权限Service接口
 */
public interface IAnchorShopService {

    /**
     * 根据主播ID查询带货权限列表
     */
    List<AnchorShopInfoDTO> listByAnchorId(Long anchorId);

    /**
     * 添加主播带货权限
     */
    void add(AnchorShopInfoDTO anchorShopInfoDTO);

    /**
     * 更新主播带货权限状态
     */
    void updateStatus(Integer id, Integer status);
}
