package org.qiyu.live.gift.provider.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.idea.qiyu.live.framework.redis.starter.key.GiftProviderCacheKeyBuilder;
import org.qiyu.live.bank.interfaces.IQiyuCurrencyAccountRpc;
import org.qiyu.live.common.interfaces.dto.RedPacketMqDTO;
import org.qiyu.live.common.interfaces.topic.GiftProviderTopicNames;
import org.qiyu.live.gift.dto.RedPacketConfigDTO;
import org.qiyu.live.gift.provider.dao.mapper.RedPacketConfigMapper;
import org.qiyu.live.gift.provider.dao.po.RedPacketConfigPO;
import org.qiyu.live.gift.provider.service.IRedPacketService;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 红包雨Service实现
 */
@Service
public class RedPacketServiceImpl implements IRedPacketService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedPacketServiceImpl.class);

    @Resource
    private RedPacketConfigMapper redPacketConfigMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private GiftProviderCacheKeyBuilder cacheKeyBuilder;
    @Resource
    private MQProducer mqProducer;
    @DubboReference
    private IQiyuCurrencyAccountRpc qiyuCurrencyAccountRpc;
    @DubboReference
    private ImRouterRpc routerRpc;

    @Override
    public RedPacketConfigDTO getByAnchorId(Long anchorId) {
        LambdaQueryWrapper<RedPacketConfigPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RedPacketConfigPO::getAnchorId, anchorId)
               .orderByDesc(RedPacketConfigPO::getCreateTime)
               .last("LIMIT 1");
        RedPacketConfigPO po = redPacketConfigMapper.selectOne(wrapper);
        return convert(po);
    }

    @Override
    public void create(RedPacketConfigDTO redPacketConfigDTO) {
        RedPacketConfigPO po = convert(redPacketConfigDTO);
        po.setStatus(1); // 待预热
        po.setTotalGet(0);
        po.setTotalGetPrice(0);
        redPacketConfigMapper.insert(po);
    }

    @Override
    public void prepare(Integer id) {
        RedPacketConfigPO po = new RedPacketConfigPO();
        po.setId(id);
        po.setStatus(2); // 已预热，待发送
        redPacketConfigMapper.updateById(po);
    }

    @Override
    public void send(Integer id) {
        // 1. 更新状态为已发送
        RedPacketConfigPO po = new RedPacketConfigPO();
        po.setId(id);
        po.setStatus(3); // 已发送
        redPacketConfigMapper.updateById(po);

        // 2. 查询红包配置
        RedPacketConfigPO redPacket = redPacketConfigMapper.selectById(id);
        if (redPacket == null) {
            return;
        }

        // 3. 发送MQ消息，通知直播间所有用户红包雨开始
        RedPacketMqDTO mqDTO = new RedPacketMqDTO();
        mqDTO.setRedPacketId(id);
        mqDTO.setAnchorId(redPacket.getAnchorId());
        mqDTO.setRoomId(redPacket.getRoomId());
        mqDTO.setConfigCode(redPacket.getConfigCode());
        mqDTO.setTotalPrice(redPacket.getTotalPrice());
        mqDTO.setTotalCount(redPacket.getTotalCount());
        mqDTO.setMaxGetPrice(redPacket.getMaxGetPrice());
        mqDTO.setType(1); // 1-发送红包雨

        Message message = new Message();
        message.setTopic(GiftProviderTopicNames.RED_PACKET_RAIN_SEND);
        message.setBody(JSON.toJSONString(mqDTO).getBytes());
        try {
            mqProducer.send(message);
            LOGGER.info("[RedPacketService] 发送红包雨MQ成功, redPacketId={}", id);
        } catch (Exception e) {
            LOGGER.error("[RedPacketService] 发送红包雨MQ失败", e);
        }
    }

    @Override
    public Integer receive(Integer id, Long userId, Integer roomId) {
        // 1. 检查红包是否存在且已发送
        RedPacketConfigPO po = redPacketConfigMapper.selectById(id);
        if (po == null || po.getStatus() != 3) {
            return 0; // 红包不存在或未发送
        }

        // 2. 检查是否还有库存
        if (po.getTotalGet() >= po.getTotalCount()) {
            return 0; // 红包已领完
        }

        // 3. 检查用户是否已领取过（用Redis Set记录）
        String receiveKey = cacheKeyBuilder.buildRedPacketReceiveKey(id);
        Boolean isMember = redisTemplate.opsForSet().isMember(receiveKey, userId);
        if (Boolean.TRUE.equals(isMember)) {
            return 0; // 已领取过
        }

        // 4. 扣减库存（乐观锁）
        int result = redPacketConfigMapper.decrementStock(id, 1);
        if (result <= 0) {
            return 0; // 库存不足
        }

        // 5. 随机金额
        int receivePrice = ThreadLocalRandom.current().nextInt(1, po.getMaxGetPrice() + 1);

        // 6. 记录用户领取（防止重复领取）
        redisTemplate.opsForSet().add(receiveKey, userId);
        redisTemplate.expire(receiveKey, 24, TimeUnit.HOURS);

        // 7. 给用户增加余额
        qiyuCurrencyAccountRpc.incr(userId, receivePrice);

        // 8. 发送MQ消息通知用户领取成功（通过IM推送）
        sendReceiveMq(id, userId, roomId, po.getAnchorId(), receivePrice, po.getConfigCode());

        return receivePrice;
    }

    @Override
    public RedPacketConfigDTO getByConfigCode(String configCode) {
        LambdaQueryWrapper<RedPacketConfigPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RedPacketConfigPO::getConfigCode, configCode)
               .orderByDesc(RedPacketConfigPO::getCreateTime)
               .last("LIMIT 1");
        RedPacketConfigPO po = redPacketConfigMapper.selectOne(wrapper);
        return convert(po);
    }

    @Override
    public void settle(Integer id) {
        // 1. 查询红包配置
        RedPacketConfigPO po = redPacketConfigMapper.selectById(id);
        if (po == null) {
            return;
        }

        // 2. 计算剩余金额
        int remainingCount = po.getTotalCount() - po.getTotalGet();
        if (remainingCount <= 0) {
            return; // 红包已全部领完，无需返还
        }

        // 3. 剩余金额 = 总金额 - 已领取金额
        int remainingPrice = po.getTotalPrice() - po.getTotalGetPrice();
        if (remainingPrice <= 0) {
            return; // 金额已全部领取完
        }

        // 4. 返还剩余金额给主播
        qiyuCurrencyAccountRpc.incr(po.getAnchorId(), remainingPrice);

        // 5. 更新状态为已结算
        RedPacketConfigPO updatePO = new RedPacketConfigPO();
        updatePO.setId(id);
        updatePO.setStatus(4); // 已结算
        redPacketConfigMapper.updateById(updatePO);

        LOGGER.info("[RedPacketService] 红包雨结算完成, redPacketId={}, 返还主播={}, 返还金额={}",
                id, po.getAnchorId(), remainingPrice);
    }

    /**
     * 发送领取成功MQ消息，通过IM推送给用户
     */
    private void sendReceiveMq(Integer redPacketId, Long userId, Integer roomId, Long anchorId, Integer receivePrice, String configCode) {
        RedPacketMqDTO mqDTO = new RedPacketMqDTO();
        mqDTO.setRedPacketId(redPacketId);
        mqDTO.setUserId(userId);
        mqDTO.setRoomId(roomId);
        mqDTO.setAnchorId(anchorId);
        mqDTO.setConfigCode(configCode);
        mqDTO.setReceivePrice(receivePrice);
        mqDTO.setType(2); // 2-领取红包

        Message message = new Message();
        message.setTopic(GiftProviderTopicNames.RED_PACKET_RAIN_RECEIVE);
        message.setBody(JSON.toJSONString(mqDTO).getBytes());
        try {
            mqProducer.send(message);
        } catch (Exception e) {
            LOGGER.error("[RedPacketService] 发送领取MQ失败", e);
        }
    }

    private RedPacketConfigDTO convert(RedPacketConfigPO po) {
        if (po == null) {
            return null;
        }
        RedPacketConfigDTO dto = new RedPacketConfigDTO();
        dto.setId(po.getId());
        dto.setAnchorId(po.getAnchorId());
        dto.setStartTime(po.getStartTime());
        dto.setTotalGet(po.getTotalGet());
        dto.setTotalGetPrice(po.getTotalGetPrice());
        dto.setMaxGetPrice(po.getMaxGetPrice());
        dto.setStatus(po.getStatus());
        dto.setTotalPrice(po.getTotalPrice());
        dto.setTotalCount(po.getTotalCount());
        dto.setConfigCode(po.getConfigCode());
        dto.setRemark(po.getRemark());
        dto.setCreateTime(po.getCreateTime());
        dto.setUpdateTime(po.getUpdateTime());
        return dto;
    }
}
