package org.qiyu.live.gift.interfaces;

import org.qiyu.live.gift.dto.RedPacketConfigDTO;

/**
 * 红包雨RPC接口
 */
public interface IRedPacketRpc {

    /**
     * 根据主播ID查询红包雨配置
     */
    RedPacketConfigDTO getByAnchorId(Long anchorId);

    /**
     * 根据配置码查询
     */
    RedPacketConfigDTO getByConfigCode(String configCode);
}
