package org.qiyu.live.user.provider.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.common.collect.Maps;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.idea.qiyu.live.framework.redis.starter.key.UserProviderCacheKeyBuilder;
import org.qiyu.live.common.interfaces.topic.UserProviderTopicNames;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.user.constants.CacheAsyncDeleteCode;
import org.qiyu.live.user.dto.UserCacheAsyncDeleteDTO;
import org.qiyu.live.common.interfaces.constants.RiskConstants;
import org.qiyu.live.user.dto.UserBanDTO;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.provider.dao.mapper.IUserBanMapper;
import org.qiyu.live.user.provider.dao.po.UserBanPO;
import org.qiyu.live.user.provider.dao.mapper.IUserMapper;
import org.qiyu.live.user.provider.dao.po.UserPO;
import org.qiyu.live.user.provider.service.IUserService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * @Author idea
 * @Date: Created in 16:40 2023/5/12
 * @Description
 */
@Service
public class UserServiceImpl implements IUserService {

    @Resource
    private IUserMapper userMapper;
    @Resource
    private IUserBanMapper userBanMapper;
    @Resource
    private RedisTemplate<String, UserDTO> redisTemplate;
    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserProviderCacheKeyBuilder cacheKeyBuilder;
    @Resource
    private MQProducer mqProducer;

    @Override
    public UserDTO getByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        String key = cacheKeyBuilder.buildUserInfoKey(userId);
        UserDTO userDTO = redisTemplate.opsForValue().get(key);
        if (userDTO != null) {
            return userDTO;
        }
        userDTO = ConvertBeanUtils.convert(userMapper.selectById(userId), UserDTO.class);
        if (userDTO != null) {
            redisTemplate.opsForValue().set(key, userDTO, 30, TimeUnit.MINUTES);
        }
        return userDTO;
    }

    @Override
    public boolean updateUserInfo(UserDTO userDTO) {
        if (userDTO == null || userDTO.getUserId() == null) {
            return false;
        }
        int updateStatus = userMapper.updateById(ConvertBeanUtils.convert(userDTO, UserPO.class));
        if (updateStatus > -1) {
            String key = cacheKeyBuilder.buildUserInfoKey(userDTO.getUserId());
            redisTemplate.delete(key);
            UserCacheAsyncDeleteDTO userCacheAsyncDeleteDTO = new UserCacheAsyncDeleteDTO();
            userCacheAsyncDeleteDTO.setCode(CacheAsyncDeleteCode.USER_INFO_DELETE.getCode());
            Map<String,Object> jsonParam = new HashMap<>();
            jsonParam.put("userId",userDTO.getUserId());
            userCacheAsyncDeleteDTO.setJson(JSON.toJSONString(jsonParam));
            Message message = new Message();
            message.setTopic(UserProviderTopicNames.CACHE_ASYNC_DELETE_TOPIC);
            message.setBody(JSON.toJSONString(userCacheAsyncDeleteDTO).getBytes());
            //延迟一秒进行缓存的二次删除
            message.setDelayTimeLevel(1);
            try {
                mqProducer.send(message);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    @Override
    public boolean insertOne(UserDTO userDTO) {
        if (userDTO == null || userDTO.getUserId() == null) {
            return false;
        }
        userMapper.insert(ConvertBeanUtils.convert(userDTO, UserPO.class));
        return true;
    }

    @Override
    public java.util.List<UserDTO> listUsers(String keyword, int page, int pageSize) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<org.qiyu.live.user.provider.dao.po.UserPO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            boolean numeric = keyword.chars().allMatch(Character::isDigit);
            // 数字优先按userId精确，其次昵称模糊
            if (numeric) {
                wrapper.and(w -> w.eq(org.qiyu.live.user.provider.dao.po.UserPO::getUserId, Long.parseLong(keyword))
                        .or().like(org.qiyu.live.user.provider.dao.po.UserPO::getNickName, keyword));
            } else {
                wrapper.like(org.qiyu.live.user.provider.dao.po.UserPO::getNickName, keyword);
            }
        }
        wrapper.orderByDesc(org.qiyu.live.user.provider.dao.po.UserPO::getUserId);
        wrapper.last(String.format("limit %d,%d", Math.max(page - 1, 0) * pageSize, pageSize));
        return org.qiyu.live.common.interfaces.utils.ConvertBeanUtils.convertList(userMapper.selectList(wrapper), UserDTO.class);
    }

    @Override
    public Map<Long, UserDTO> batchQueryUserInfo(List<Long> userIdList) {
        if (CollectionUtils.isEmpty(userIdList)) {
            return Maps.newHashMap();
        }
        userIdList = userIdList.stream().filter(id -> id > 10000).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(userIdList)) {
            return Maps.newHashMap();
        }
        // redis
        List<String> keyList = new ArrayList<>();
        userIdList.forEach(userId -> {
            keyList.add(cacheKeyBuilder.buildUserInfoKey(userId));
        });
        List<UserDTO> userDTOList = redisTemplate.opsForValue().multiGet(keyList).stream().filter(x -> x != null).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(userDTOList) && userDTOList.size() == userIdList.size()) {
            return userDTOList.stream().collect(Collectors.toMap(UserDTO::getUserId, x -> x));
        }
        List<Long> userIdInCacheList = userDTOList.stream().map(UserDTO::getUserId).collect(Collectors.toList());
        List<Long> userIdNotInCacheList = userIdList.stream().filter(x -> !userIdInCacheList.contains(x)).collect(Collectors.toList());
        // 多线程查询 替换了union all
        Map<Long, List<Long>> userIdMap = userIdNotInCacheList.stream().collect(Collectors.groupingBy(userId -> userId % 100));
        List<UserDTO> dbQueryResult = new CopyOnWriteArrayList<>();
        userIdMap.values().parallelStream().forEach(queryUserIdList -> {
            dbQueryResult.addAll(ConvertBeanUtils.convertList(userMapper.selectBatchIds(queryUserIdList), UserDTO.class));
        });
        if (!CollectionUtils.isEmpty(dbQueryResult)) {
            Map<String, UserDTO> saveCacheMap = dbQueryResult.stream().collect(Collectors.toMap(userDto -> cacheKeyBuilder.buildUserInfoKey(userDto.getUserId()), x -> x));
            redisTemplate.opsForValue().multiSet(saveCacheMap);
            //对命令执行批量过期设置操作
            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    for (String redisKey : saveCacheMap.keySet()) {
                        operations.expire((K) redisKey, createRandomTime(), TimeUnit.SECONDS);
                    }
                    return null;
                }
            });
            userDTOList.addAll(dbQueryResult);
        }
        return userDTOList.stream().collect(Collectors.toMap(UserDTO::getUserId, x -> x));
    }

    /**
     * 创建随机的过期时间 用于redis设置key过期
     *
     * @return
     */
    private int createRandomTime() {
        int randomNumSecond = ThreadLocalRandom.current().nextInt(10000);
        return randomNumSecond + 30 * 60;
    }

    @Override
    public boolean banUser(UserBanDTO banDTO) {
        if (banDTO == null || banDTO.getUserId() == null || banDTO.getType() == null) {
            return false;
        }
        int minutes = banDTO.getDurationMinutes() == null ? 0 : banDTO.getDurationMinutes();
        Date endTime = minutes > 0 ? new Date(System.currentTimeMillis() + minutes * 60_000L) : null;
        UserBanPO po = new UserBanPO();
        po.setUserId(banDTO.getUserId());
        po.setType(banDTO.getType());
        po.setReason(banDTO.getReason() == null ? "" : banDTO.getReason());
        po.setStartTime(new Date());
        po.setEndTime(endTime);
        po.setOperator("admin");
        po.setStatus(1);
        userBanMapper.insert(po);
        //运行时校验 key：网关查封号、msg-provider 查禁言；key 值存原因便于排查
        String banKey = banDTO.getType() == UserBanDTO.TYPE_ACCOUNT_BAN
                ? RiskConstants.BAN_ACCOUNT_KEY_PREFIX + banDTO.getUserId()
                : RiskConstants.BAN_MUTE_KEY_PREFIX + banDTO.getUserId();
        long ttlSeconds = minutes > 0 ? minutes * 60L : 365L * 24 * 3600;
        stringRedisTemplate.opsForValue().set(banKey, banDTO.getReason() == null ? "1" : banDTO.getReason(),
                ttlSeconds, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public boolean unbanUser(Long userId, int type) {
        LambdaUpdateWrapper<UserBanPO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserBanPO::getUserId, userId)
                .eq(UserBanPO::getType, type)
                .eq(UserBanPO::getStatus, 1)
                .set(UserBanPO::getStatus, 0);
        int rows = userBanMapper.update(null, wrapper);
        String banKey = type == UserBanDTO.TYPE_ACCOUNT_BAN
                ? RiskConstants.BAN_ACCOUNT_KEY_PREFIX + userId
                : RiskConstants.BAN_MUTE_KEY_PREFIX + userId;
        stringRedisTemplate.delete(banKey);
        return rows > 0;
    }
}