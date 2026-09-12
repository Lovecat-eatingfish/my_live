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

    /**
     * 创建红包雨配置
     */
    void create(RedPacketConfigDTO redPacketConfigDTO);

    /**
     * 预热红包雨（前端准备倒计时）
     */
    void prepare(Integer id);

    /**
     * 发送红包雨（主播端触发，先扣主播金币，余额不足返回false）
     */
    boolean send(Integer id);

    /**
     * 领取红包雨
     * @return 领取金额，0表示领取失败
     */
    Integer receive(Integer id, Long userId, Integer roomId);

    /**
     * 结算红包雨（结束后返还剩余金额给主播）
     */
    void settle(Integer id);
}
