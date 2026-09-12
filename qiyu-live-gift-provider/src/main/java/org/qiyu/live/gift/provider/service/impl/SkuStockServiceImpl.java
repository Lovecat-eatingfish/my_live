package org.qiyu.live.gift.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.idea.qiyu.live.framework.redis.starter.key.GiftProviderCacheKeyBuilder;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.gift.dto.SkuStockInfoDTO;
import org.qiyu.live.gift.provider.dao.mapper.SkuStockInfoMapper;
import org.qiyu.live.gift.provider.dao.po.SkuStockInfoPO;
import org.qiyu.live.gift.provider.service.ISkuStockService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * SKU库存Service实现
 * 库存读取走redis缓存（懒加载预热），扣减以DB乐观锁为准+失败重试，扣减成功后同步redis
 */
@Service
public class SkuStockServiceImpl implements ISkuStockService {

    @Resource
    private SkuStockInfoMapper skuStockInfoMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private GiftProviderCacheKeyBuilder cacheKeyBuilder;

    @Override
    public SkuStockInfoDTO getBySkuId(Integer skuId) {
        return ConvertBeanUtils.convert(this.getBySkuIdPO(skuId), SkuStockInfoDTO.class);
    }

    @Override
    public SkuStockInfoPO getBySkuIdPO(Integer skuId) {
        //库存是下单必访问的热点数据，读取走redis缓存（懒加载预热），减轻DB压力
        String stockCacheKey = cacheKeyBuilder.buildSkuStockCacheKey(skuId);
        Object cacheValue = redisTemplate.opsForValue().get(stockCacheKey);
        if (cacheValue instanceof SkuStockInfoPO) {
            return (SkuStockInfoPO) cacheValue;
        }
        LambdaQueryWrapper<SkuStockInfoPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkuStockInfoPO::getSkuId, skuId);
        SkuStockInfoPO po = skuStockInfoMapper.selectOne(wrapper);
        if (po != null) {
            //固定时间+随机时间，避免缓存雪崩
            redisTemplate.opsForValue().set(stockCacheKey, po,
                    1800 + ThreadLocalRandom.current().nextLong(1000), TimeUnit.SECONDS);
        }
        return po;
    }

    @Override
    public boolean decrementStock(Integer skuId, Integer num) {
        //redis库存快速失败：挡掉大部分库存不足的无效请求，减少DB压力
        String stockCacheKey = cacheKeyBuilder.buildSkuStockCacheKey(skuId);
        Object cacheValue = redisTemplate.opsForValue().get(stockCacheKey);
        if (cacheValue instanceof SkuStockInfoPO
                && ((SkuStockInfoPO) cacheValue).getStockNum() < num) {
            return false;
        }
        //DB乐观锁扣减 + 失败重试：并发冲突时重读最新version再尝试，最多3次
        for (int i = 0; i < 3; i++) {
            LambdaQueryWrapper<SkuStockInfoPO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SkuStockInfoPO::getSkuId, skuId);
            SkuStockInfoPO stockPO = skuStockInfoMapper.selectOne(wrapper);
            if (stockPO == null || stockPO.getStockNum() < num) {
                return false;
            }
            if (skuStockInfoMapper.decrementStockWithVersion(skuId, num, stockPO.getVersion()) > 0) {
                //扣减成功后同步redis缓存中的库存
                if (cacheValue instanceof SkuStockInfoPO) {
                    ((SkuStockInfoPO) cacheValue).setStockNum(stockPO.getStockNum() - num);
                    ((SkuStockInfoPO) cacheValue).setVersion(stockPO.getVersion() + 1);
                    redisTemplate.opsForValue().set(stockCacheKey, cacheValue,
                            1800 + ThreadLocalRandom.current().nextLong(1000), TimeUnit.SECONDS);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void increaseStock(Integer skuId, Integer num) {
        SkuStockInfoPO po = this.getBySkuIdPO(skuId);
        if (po == null) {
            po = new SkuStockInfoPO();
            po.setSkuId(skuId);
            po.setStockNum(num);
            po.setStatus(1);
            po.setVersion(0);
            skuStockInfoMapper.insert(po);
        } else {
            SkuStockInfoPO updatePO = new SkuStockInfoPO();
            updatePO.setId(po.getId());
            updatePO.setStockNum(po.getStockNum() + num);
            skuStockInfoMapper.updateById(updatePO);
        }
        //库存回滚（超时订单）后同步失效redis缓存，下次读取重新加载
        redisTemplate.delete(cacheKeyBuilder.buildSkuStockCacheKey(skuId));
    }
}
