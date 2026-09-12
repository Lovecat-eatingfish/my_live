package org.qiyu.live.gift.provider.service.impl;

import com.alibaba.fastjson.JSON;
import org.idea.qiyu.live.framework.redis.starter.key.GiftProviderCacheKeyBuilder;
import org.qiyu.live.gift.dto.CartItemDTO;
import org.qiyu.live.gift.dto.SkuInfoDTO;
import org.qiyu.live.gift.provider.service.ICartService;
import org.qiyu.live.gift.provider.service.ISkuService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 直播带货购物车Service实现
 * 存储结构：redis hash，key=userId:roomId（直播间维度），hashKey=skuId，value=商品数量
 * 带货商品只属于当前直播间，购物车数据设置过期时间随直播场景自然消亡
 */
@Service
public class CartServiceImpl implements ICartService {

    /**
     * 购物车过期时间：12小时（覆盖一场直播的生命周期）
     */
    private static final long CART_EXPIRE_HOURS = 12;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private GiftProviderCacheKeyBuilder cacheKeyBuilder;
    @Resource
    private ISkuService skuService;

    @Override
    public void addToCart(Long userId, Integer roomId, Integer skuId, Integer num) {
        String cartKey = cacheKeyBuilder.buildCartKey(userId, roomId);
        redisTemplate.opsForHash().increment(cartKey, String.valueOf(skuId), num);
        redisTemplate.expire(cartKey, CART_EXPIRE_HOURS, TimeUnit.HOURS);
    }

    @Override
    public void updateCartNum(Long userId, Integer roomId, Integer skuId, Integer num) {
        String cartKey = cacheKeyBuilder.buildCartKey(userId, roomId);
        if (num <= 0) {
            redisTemplate.opsForHash().delete(cartKey, String.valueOf(skuId));
            return;
        }
        redisTemplate.opsForHash().put(cartKey, String.valueOf(skuId), num);
        redisTemplate.expire(cartKey, CART_EXPIRE_HOURS, TimeUnit.HOURS);
    }

    @Override
    public void removeCartItem(Long userId, Integer roomId, Integer skuId) {
        redisTemplate.opsForHash().delete(cacheKeyBuilder.buildCartKey(userId, roomId), String.valueOf(skuId));
    }

    @Override
    public void clearCart(Long userId, Integer roomId) {
        redisTemplate.delete(cacheKeyBuilder.buildCartKey(userId, roomId));
    }

    @Override
    public List<CartItemDTO> listCart(Long userId, Integer roomId) {
        String cartKey = cacheKeyBuilder.buildCartKey(userId, roomId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey);
        if (CollectionUtils.isEmpty(entries)) {
            return new ArrayList<>();
        }
        List<CartItemDTO> resultList = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Integer skuId = Integer.valueOf(String.valueOf(entry.getKey()));
            int num = ((Number) entry.getValue()).intValue();
            SkuInfoDTO skuInfo = skuService.getBySkuId(skuId);
            if (skuInfo == null) {
                continue;
            }
            CartItemDTO itemDTO = new CartItemDTO();
            itemDTO.setSkuId(skuId);
            itemDTO.setNum(num);
            itemDTO.setName(skuInfo.getName());
            itemDTO.setIconUrl(skuInfo.getIconUrl());
            itemDTO.setSkuPrice(skuInfo.getSkuPrice());
            itemDTO.setRemark(skuInfo.getRemark());
            resultList.add(itemDTO);
        }
        return resultList;
    }

    @Override
    public Integer countCart(Long userId, Integer roomId) {
        return redisTemplate.opsForHash().size(cacheKeyBuilder.buildCartKey(userId, roomId)).intValue();
    }
}
