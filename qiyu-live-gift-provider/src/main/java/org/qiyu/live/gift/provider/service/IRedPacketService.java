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
     * 发送红包雨（主播点击发送，先扣主播金币，余额不足返回false）
     * @param id 红包配置ID
     */
    boolean send(Integer id);

    /**
     * 用户领取红包
     * @param id 红包配置ID
     * @param userId 用户ID
     * @param roomId 直播间ID
     * @return 领取到的金额，0表示领取失败（已领完或已领取过）
     */
    Integer receive(Integer id, Long userId, Integer roomId);

    /**
     * 领取统计异步同步到DB（由MQ消费者调用，DB中的统计供结算使用）
     * @param id 红包配置ID
     * @param receivePrice 本次领取金额
     */
    void syncReceiveStat(Integer id, int receivePrice);

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
