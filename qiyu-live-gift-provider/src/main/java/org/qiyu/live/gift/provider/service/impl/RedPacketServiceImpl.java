package org.qiyu.live.gift.provider.service.impl;

import com.alibaba.fastjson.JSON;
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
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
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
    @DubboReference(check = false)
    private IQiyuCurrencyAccountRpc qiyuCurrencyAccountRpc;
    @DubboReference(check = false)
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
        RedPacketConfigPO po = convertToPO(redPacketConfigDTO);
        if (po.getConfigCode() == null || po.getConfigCode().isEmpty()) {
            po.setConfigCode(java.util.UUID.randomUUID().toString().replace("-", ""));
        }
        po.setStatus(1); // 待预热
        po.setTotalGet(0);
        po.setTotalGetPrice(0);
        redPacketConfigMapper.insert(po);
    }

    @Override
    public void prepare(Integer id) {
        RedPacketConfigPO po = redPacketConfigMapper.selectById(id);
        if (po == null) {
            return;
        }
        //分布式锁防止主播重复点击导致红包重复生成
        String lockKey = cacheKeyBuilder.buildRedPacketPrepareLockKey(po.getConfigCode());
        Boolean isLock = redisTemplate.opsForValue().setIfAbsent(lockKey, 1, 3L, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(isLock)) {
            return;
        }
        try {
            //已准备过直接返回（Redis标记，防止未准备就开始红包雨）
            String preparedFlagKey = cacheKeyBuilder.buildRedPacketPreparedFlagKey(po.getConfigCode());
            if (Boolean.TRUE.equals(redisTemplate.hasKey(preparedFlagKey))) {
                return;
            }
            //两倍随机法分割红包金额，保证随机均匀且总额恒等于配置总金额
            List<Integer> priceList = this.createRedPacketPriceList(po.getTotalPrice(), po.getTotalCount());
            //红包池以configCode为key（而非主播id/红包id），避免上一场未领完的红包混入下一场
            String listKey = cacheKeyBuilder.buildRedPacketListKey(po.getConfigCode());
            //分批插入，避免一次大命令阻塞Redis单线程
            //显式构造List<Object>以匹配rightPushAll(key, Collection)重载，否则会把整个List当单个元素push
            for (int i = 0; i < priceList.size(); i += 100) {
                List<Object> batch = new ArrayList<>(priceList.subList(i, Math.min(i + 100, priceList.size())));
                redisTemplate.opsForList().rightPushAll(listKey, batch);
            }
            redisTemplate.expire(listKey, 1, TimeUnit.DAYS);
            //更新DB状态为已准备
            RedPacketConfigPO updatePO = new RedPacketConfigPO();
            updatePO.setId(id);
            updatePO.setStatus(2); // 已准备，待发送
            redPacketConfigMapper.updateById(updatePO);
            redisTemplate.opsForValue().set(preparedFlagKey, 1, 1, TimeUnit.DAYS);
            LOGGER.info("[RedPacketService] 红包池准备完成, redPacketId={}, count={}, totalPrice={}", id, priceList.size(), po.getTotalPrice());
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 两倍随机法分割红包金额：每次随机上限为剩余均值的2倍，最后一个红包拿剩余全部
     */
    private List<Integer> createRedPacketPriceList(int totalPrice, int totalCount) {
        List<Integer> priceList = new ArrayList<>(totalCount);
        int remainPrice = Math.max(totalPrice, totalCount);
        int remainCount = totalCount;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < totalCount; i++) {
            if (remainCount <= 1) {
                priceList.add(remainPrice);
                break;
            }
            int max = Math.max(1, remainPrice / remainCount * 2 - 1);
            int price = random.nextInt(1, max + 1);
            //保证剩余金额足够剩下的红包每人至少1
            price = Math.min(price, remainPrice - (remainCount - 1));
            priceList.add(price);
            remainPrice -= price;
            remainCount--;
        }
        return priceList;
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

        // 2. 红包池必须已准备（防止跳过准备直接领取）
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(cacheKeyBuilder.buildRedPacketPreparedFlagKey(po.getConfigCode())))) {
            return 0;
        }

        // 3. 检查用户是否已领取过（用Redis Set记录）
        String receiveKey = cacheKeyBuilder.buildRedPacketReceiveKey(id);
        if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(receiveKey, userId))) {
            return 0; // 已领取过
        }

        // 4. 从红包池原子弹出一个金额：rightPop线程安全，红包个数由池子大小天然控制，不会超发
        String listKey = cacheKeyBuilder.buildRedPacketListKey(po.getConfigCode());
        Object priceObj = redisTemplate.opsForList().rightPop(listKey);
        if (priceObj == null) {
            return 0; // 红包已领完
        }
        int receivePrice = ((Number) priceObj).intValue();

        // 5. 记录用户领取（防止重复领取）
        redisTemplate.opsForSet().add(receiveKey, userId);
        redisTemplate.expire(receiveKey, 24, TimeUnit.HOURS);

        // 6. 实时统计领取个数和金额（hash自增），由MQ消费者异步同步到DB
        String statKey = cacheKeyBuilder.buildRedPacketStatKey(id);
        redisTemplate.opsForHash().increment(statKey, "totalGet", 1);
        redisTemplate.opsForHash().increment(statKey, "totalGetPrice", receivePrice);
        redisTemplate.expire(statKey, 1, TimeUnit.DAYS);

        // 7. 给用户增加余额
        qiyuCurrencyAccountRpc.incr(userId, receivePrice);

        // 8. 发送MQ消息通知用户领取成功（通过IM推送）+ 异步同步DB统计
        sendReceiveMq(id, userId, roomId, po.getAnchorId(), receivePrice, po.getConfigCode());

        return receivePrice;
    }

    @Override
    public void syncReceiveStat(Integer id, int receivePrice) {
        redPacketConfigMapper.incrReceiveStat(id, receivePrice);
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
        dto.setRoomId(po.getRoomId());
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

    private RedPacketConfigPO convertToPO(RedPacketConfigDTO dto) {
        if (dto == null) {
            return null;
        }
        RedPacketConfigPO po = new RedPacketConfigPO();
        po.setId(dto.getId());
        po.setAnchorId(dto.getAnchorId());
        po.setRoomId(dto.getRoomId());
        po.setStartTime(dto.getStartTime());
        po.setTotalGet(dto.getTotalGet());
        po.setTotalGetPrice(dto.getTotalGetPrice());
        po.setMaxGetPrice(dto.getMaxGetPrice());
        po.setStatus(dto.getStatus());
        po.setTotalPrice(dto.getTotalPrice());
        po.setTotalCount(dto.getTotalCount());
        po.setConfigCode(dto.getConfigCode());
        po.setRemark(dto.getRemark());
        return po;
    }
}
