package org.qiyu.live.gift.provider.service;

import org.qiyu.live.gift.dto.SkuOrderInfoDTO;

import java.util.List;

/**
 * 商品订单Service接口
 */
public interface ISkuOrderService {

    /**
     * 创建订单（下单时扣减库存，发送MQ延迟消息用于超时回滚）
     * @param orderInfoDTO 订单信息（包含用户ID、直播间ID、SKU列表、总价等）
     * @return 创建成功的订单
     */
    SkuOrderInfoDTO createOrder(SkuOrderInfoDTO orderInfoDTO);

    /**
     * 支付成功（确认订单，扣除用户余额）
     * @param orderId 订单ID
     * @return true-成功 false-失败
     */
    boolean paySuccess(Integer orderId);

    /**
     * 订单超时回滚（释放库存）
     * @param orderId 订单ID
     */
    void timeoutRollback(Integer orderId);

    /**
     * 根据用户ID查询订单列表
     */
    List<SkuOrderInfoDTO> listByUserId(Long userId);

    /**
     * 更新订单状态
     */
    void updateStatus(Integer id, Integer status);

    /**
     * 根据ID查询订单
     */
    SkuOrderInfoDTO getById(Integer id);
}
