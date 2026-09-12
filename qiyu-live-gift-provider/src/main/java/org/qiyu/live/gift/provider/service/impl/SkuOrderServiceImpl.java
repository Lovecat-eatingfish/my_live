package org.qiyu.live.gift.provider.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.idea.qiyu.live.framework.redis.starter.key.GiftProviderCacheKeyBuilder;
import org.qiyu.live.bank.dto.AccountTradeReqDTO;
import org.qiyu.live.bank.dto.AccountTradeRespDTO;
import org.qiyu.live.bank.interfaces.IQiyuCurrencyAccountRpc;
import org.qiyu.live.common.interfaces.dto.SkuOrderMqDTO;
import org.qiyu.live.common.interfaces.topic.GiftProviderTopicNames;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.gift.dto.SkuInfoDTO;
import org.qiyu.live.gift.dto.SkuOrderInfoDTO;
import org.qiyu.live.gift.provider.dao.mapper.SkuOrderInfoMapper;
import org.qiyu.live.gift.provider.dao.mapper.SkuStockInfoMapper;
import org.qiyu.live.gift.provider.dao.po.SkuOrderInfoPO;
import org.qiyu.live.gift.provider.dao.po.SkuStockInfoPO;
import org.qiyu.live.gift.provider.service.ISkuOrderService;
import org.qiyu.live.gift.provider.service.ISkuService;
import org.qiyu.live.gift.provider.service.ISkuStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

/**
 * 商品订单 Service实现
 */
@Service
public class SkuOrderServiceImpl implements ISkuOrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkuOrderServiceImpl.class);

    @Resource
    private SkuOrderInfoMapper skuOrderInfoMapper;
    @Resource
    private SkuStockInfoMapper skuStockInfoMapper;
    @Resource
    private ISkuStockService skuStockService;
    @Resource
    private ISkuService skuService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private GiftProviderCacheKeyBuilder cacheKeyBuilder;
    @Resource
    private MQProducer mqProducer;
    @DubboReference(check = false)
    private IQiyuCurrencyAccountRpc qiyuCurrencyAccountRpc;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkuOrderInfoDTO createOrder(SkuOrderInfoDTO orderInfoDTO) {
        // 1. 解析SKU列表
        String skuIdListStr = orderInfoDTO.getSkuIdList();
        String[] skuIds = skuIdListStr.split(",");
        int totalPrice = 0;

        // 2. 预扣库存（分布式锁保证原子性），并计算总价
        for (String skuIdStr : skuIds) {
            Integer skuId = Integer.parseInt(skuIdStr.trim());
            SkuStockInfoPO stockPO = skuStockService.getBySkuIdPO(skuId);
            if (stockPO == null || stockPO.getStockNum() < 1) {
                throw new RuntimeException("SKU库存不足: " + skuId);
            }
            // 扣减库存（预扣）
            boolean deductSuccess = skuStockService.decrementStock(skuId, 1);
            if (!deductSuccess) {
                throw new RuntimeException("SKU库存扣减失败: " + skuId);
            }
            // 计算总价
            SkuInfoDTO skuInfo = skuService.getBySkuId(skuId);
            if (skuInfo != null) {
                totalPrice += skuInfo.getSkuPrice();
            }
        }

        // 3. 创建订单
        SkuOrderInfoPO po = ConvertBeanUtils.convert(orderInfoDTO, SkuOrderInfoPO.class);
        po.setTotalPrice(totalPrice);
        po.setStatus(0); // 待支付
        skuOrderInfoMapper.insert(po);

        // 4. 发送MQ延迟消息（30分钟后超时回滚）
        SkuOrderMqDTO mqDTO = new SkuOrderMqDTO();
        mqDTO.setOrderId(po.getId());
        mqDTO.setUserId(orderInfoDTO.getUserId());
        mqDTO.setRoomId(orderInfoDTO.getRoomId());
        mqDTO.setSkuIdList(skuIdListStr);
        mqDTO.setTotalPrice(totalPrice);
        mqDTO.setType(1); // 1-创建订单

        Message message = new Message();
        message.setTopic(GiftProviderTopicNames.SKU_ORDER_TIMEOUT);
        message.setBody(JSON.toJSONString(mqDTO).getBytes());
        message.setDelayTimeLevel(15); // 30分钟（RocketMQ延迟级别15对应30分钟）

        try {
            mqProducer.send(message);
            LOGGER.info("[SkuOrderService] 创建订单成功，发送超时回滚MQ, orderId={}, totalPrice={}", po.getId(), totalPrice);
        } catch (Exception e) {
            LOGGER.error("[SkuOrderService] 发送超时回滚MQ失败", e);
        }

        return ConvertBeanUtils.convert(po, SkuOrderInfoDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean paySuccess(Integer orderId) {
        // 1. 查询订单
        SkuOrderInfoPO orderPO = skuOrderInfoMapper.selectById(orderId);
        if (orderPO == null || orderPO.getStatus() != 0) {
            return false; // 订单不存在或已支付
        }

        // 2. 扣除用户余额
        AccountTradeReqDTO tradeReqDTO = new AccountTradeReqDTO();
        tradeReqDTO.setUserId(orderPO.getUserId());
        tradeReqDTO.setNum(orderPO.getTotalPrice());
        AccountTradeRespDTO tradeRespDTO = qiyuCurrencyAccountRpc.consumeForSendGift(tradeReqDTO);

        if (!tradeRespDTO.isSuccess()) {
            LOGGER.info("[SkuOrderService] 支付失败，余额不足, orderId={}", orderId);
            return false;
        }

        // 3. 更新订单状态为已支付
        SkuOrderInfoPO updatePO = new SkuOrderInfoPO();
        updatePO.setId(orderId);
        updatePO.setStatus(1); // 已支付
        skuOrderInfoMapper.updateById(updatePO);

        // 4. 发送MQ通知订单状态变更
        sendStatusChangeMq(orderId, orderPO.getUserId(), orderPO.getRoomId(), 1);

        LOGGER.info("[SkuOrderService] 支付成功, orderId={}", orderId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void timeoutRollback(Integer orderId) {
        // 1. 查询订单
        SkuOrderInfoPO orderPO = skuOrderInfoMapper.selectById(orderId);
        if (orderPO == null || orderPO.getStatus() != 0) {
            return; // 订单不存在或已处理
        }

        // 2. 释放库存
        String[] skuIds = orderPO.getSkuIdList().split(",");
        for (String skuIdStr : skuIds) {
            Integer skuId = Integer.parseInt(skuIdStr.trim());
            skuStockService.increaseStock(skuId, 1);
        }

        // 3. 更新订单状态为已取消
        SkuOrderInfoPO updatePO = new SkuOrderInfoPO();
        updatePO.setId(orderId);
        updatePO.setStatus(2); // 已取消（超时）
        skuOrderInfoMapper.updateById(updatePO);

        // 4. 发送MQ通知订单状态变更
        sendStatusChangeMq(orderId, orderPO.getUserId(), orderPO.getRoomId(), 2);

        LOGGER.info("[SkuOrderService] 订单超时回滚成功, orderId={}", orderId);
    }

    @Override
    public SkuOrderInfoDTO getById(Integer id) {
        SkuOrderInfoPO po = skuOrderInfoMapper.selectById(id);
        return ConvertBeanUtils.convert(po, SkuOrderInfoDTO.class);
    }

    @Override
    public java.util.List<SkuOrderInfoDTO> listByUserId(Long userId) {
        LambdaQueryWrapper<SkuOrderInfoPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkuOrderInfoPO::getUserId, userId)
               .orderByDesc(SkuOrderInfoPO::getCreateTime);
        java.util.List<SkuOrderInfoPO> poList = skuOrderInfoMapper.selectList(wrapper);
        return ConvertBeanUtils.convertList(poList, SkuOrderInfoDTO.class);
    }

    @Override
    public void updateStatus(Integer id, Integer status) {
        SkuOrderInfoPO po = new SkuOrderInfoPO();
        po.setId(id);
        po.setStatus(status);
        skuOrderInfoMapper.updateById(po);
    }

    /**
     * 发送订单状态变更MQ消息
     */
    private void sendStatusChangeMq(Integer orderId, Long userId, Integer roomId, Integer status) {
        SkuOrderMqDTO mqDTO = new SkuOrderMqDTO();
        mqDTO.setOrderId(orderId);
        mqDTO.setUserId(userId);
        mqDTO.setRoomId(roomId);
        mqDTO.setType(3); // 3-状态变更

        Message message = new Message();
        message.setTopic(GiftProviderTopicNames.ORDER_STATUS_CHANGE);
        message.setBody(JSON.toJSONString(mqDTO).getBytes());
        try {
            mqProducer.send(message);
        } catch (Exception e) {
            LOGGER.error("[SkuOrderService] 发送订单状态变更MQ失败", e);
        }
    }
}
