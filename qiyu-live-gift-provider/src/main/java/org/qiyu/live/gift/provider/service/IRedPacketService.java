package org.qiyu.live.gift.provider.service;

import org.qiyu.live.gift.dto.RedPacketConfigDTO;

/**
 * 红包雨Service接口
 */
public interface IRedPacketService {

    /**
     * 根据主播ID查询最新的红包雨配置
     */
    RedPacketConfigDTO getByAnchorId(Long anchorId);

    /**
     * 创建红包雨配置
     * @param redPacketConfigDTO 包含主播ID、直播间ID、总金额、总数量、最大领取金额等
     */
    void create(RedPacketConfigDTO redPacketConfigDTO);

    /**
     * 预热红包雨（主播点击准备发放）
     * @param id 红包配置ID
     */
    void prepare(Integer id);

    /**
     * 发送红包雨（主播点击发送，通过MQ+IM推送给直播间用户）
     * @param id 红包配置ID
     */
    void send(Integer id);

    /**
     * 用户领取红包
     * @param id 红包配置ID
     * @param userId 用户ID
     * @param roomId 直播间ID
     * @return 领取到的金额，0表示领取失败（已领完或已领取过）
     */
    Integer receive(Integer id, Long userId, Integer roomId);

    /**
     * 根据配置码查询
     */
    RedPacketConfigDTO getByConfigCode(String configCode);

    /**
     * 结算红包雨（红包雨结束后，剩余金额返还主播）
     * @param id 红包配置ID
     */
    void settle(Integer id);
}
