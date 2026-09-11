package org.qiyu.live.stream.provider.service.impl;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.qiyu.live.stream.interfaces.dto.LivingStreamPushUrlDTO;
import org.qiyu.live.stream.interfaces.dto.StreamStatusDTO;
import org.qiyu.live.stream.provider.config.SrsConfig;
import org.qiyu.live.stream.provider.config.StreamProviderCacheKeyBuilder;
import org.qiyu.live.stream.provider.dao.mapper.LivingRoomMapper;
import org.qiyu.live.stream.provider.dao.po.LivingRoomPO;
import org.qiyu.live.stream.provider.service.ILivingStreamService;
import org.qiyu.live.stream.provider.service.ISrsApiService;

/**
 * 直播推流服务实现
 */
@Service
public class LivingStreamServiceImpl implements ILivingStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivingStreamServiceImpl.class);

    private static final int STREAM_STATUS_NOT_START = 0;
    private static final int STREAM_STATUS_LIVING = 1;
    private static final int STREAM_STATUS_ERROR = 2;

    @Resource
    private LivingRoomMapper livingRoomMapper;
    @Resource
    private SrsConfig srsConfig;
    @Resource
    private StreamProviderCacheKeyBuilder cacheKeyBuilder;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private ISrsApiService srsApiService;

    @Override
    public LivingStreamPushUrlDTO createPushUrl(Integer roomId, Long anchorId) {
        // 1. 生成带签名的 streamKey，防伪造
        String streamKey = buildStreamKey(roomId, anchorId);
        // 2. 拼接推流地址
        String pushUrl = srsConfig.getRtmpBaseUrl() + "/" + streamKey;

        // 3. 存储 streamKey 到数据库
        livingRoomMapper.updateStreamKey(roomId, streamKey, pushUrl);

        // 4. 缓存到 Redis（用于回调时快速校验归属）
        String cacheKey = cacheKeyBuilder.buildStreamKey(roomId);
        redisTemplate.opsForValue().set(cacheKey, streamKey, 24, TimeUnit.HOURS);

        // 5. 返回结果
        LivingStreamPushUrlDTO dto = new LivingStreamPushUrlDTO();
        dto.setPushUrl(pushUrl);
        dto.setStreamKey(streamKey);
        dto.setExpireTime(System.currentTimeMillis() + 24 * 3600 * 1000L);
        LOGGER.info("[createPushUrl] roomId={}, anchorId={}, pushUrl={}", roomId, anchorId, pushUrl);
        return dto;
    }

    @Override
    public StreamStatusDTO getStreamStatus(Integer roomId) {
        StreamStatusDTO dto = new StreamStatusDTO();
        dto.setRoomId(roomId);

        // 1. 先查数据库
        LivingRoomPO room = livingRoomMapper.selectById(roomId);
        if (room == null) {
            dto.setStatus(STREAM_STATUS_NOT_START);
            return dto;
        }

        Integer dbStatus = room.getStreamStatus();
        dto.setStatus(dbStatus != null ? dbStatus : STREAM_STATUS_NOT_START);

        // 2. 如果是开播状态，去 SRS 校验真实情况
        if (STREAM_STATUS_LIVING.equals(dbStatus)) {
            String streamKey = room.getStreamKey();
            if (StringUtils.hasText(streamKey)) {
                boolean online = srsApiService.isStreamOnline(streamKey);
                if (!online) {
                    // SRS 已无流，修正状态并写回数据库
                    dto.setStatus(STREAM_STATUS_ERROR);
                    dto.setViewerCount(0);
                    fixStreamStatusAsync(roomId, streamKey, STREAM_STATUS_ERROR);
                    return dto;
                }
                dto.setViewerCount(srsApiService.getViewerCount(streamKey));
            }
        }

        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean stopStream(Integer roomId) {
        LivingRoomPO room = livingRoomMapper.selectById(roomId);
        if (room == null) {
            return false;
        }
        String streamKey = room.getStreamKey();
        // 重置数据库流状态
        livingRoomMapper.resetStream(roomId);
        // 清除 Redis 缓存
        redisTemplate.delete(cacheKeyBuilder.buildStreamKey(roomId));
        redisTemplate.delete(cacheKeyBuilder.buildStreamStatus(roomId));
        LOGGER.info("[stopStream] roomId={}, streamKey={}", roomId, streamKey);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPublish(String streamKey, String clientId, String ip) {
        LOGGER.info("[onPublish] streamKey={}, clientId={}, ip={}", streamKey, clientId, ip);
        // 1. 从 streamKey 解析出 roomId
        Integer roomId = parseRoomIdFromStreamKey(streamKey);
        if (roomId == null) {
            LOGGER.warn("[onPublish] cannot parse roomId from streamKey={}", streamKey);
            return;
        }
        // 2. 校验 streamKey 归属（防止伪造推流）
        String cachedKey = (String) redisTemplate.opsForValue().get(cacheKeyBuilder.buildStreamKey(roomId));
        if (!streamKey.equals(cachedKey)) {
            LOGGER.warn("[onPublish] streamKey mismatch, roomId={}, expected={}, got={}",
                    roomId, cachedKey, streamKey);
            return;
        }
        // 3. 更新数据库流状态
        livingRoomMapper.updateStreamStatus(roomId, STREAM_STATUS_LIVING, new Date());
        // 4. 缓存状态到 Redis
        String cacheKey = cacheKeyBuilder.buildStreamStatus(roomId);
        redisTemplate.opsForValue().set(cacheKey, STREAM_STATUS_LIVING, 24, TimeUnit.HOURS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onUnpublish(String streamKey) {
        LOGGER.info("[onUnpublish] streamKey={}", streamKey);
        Integer roomId = parseRoomIdFromStreamKey(streamKey);
        if (roomId == null) {
            return;
        }
        livingRoomMapper.updateStreamStatus(roomId, STREAM_STATUS_NOT_START, null);
        redisTemplate.delete(cacheKeyBuilder.buildStreamStatus(roomId));
    }

    /**
     * 生成带签名的 streamKey，防止伪造
     * 格式: live_{md5(roomId_anchorId_secret)}
     */
    private String buildStreamKey(Integer roomId, Long anchorId) {
        String raw = roomId + "_" + anchorId + "_" + srsConfig.getSecret();
        String md5 = md5(raw);
        return "live_" + md5;
    }

    /**
     * 从 streamKey 解析 roomId
     * 由于 streamKey 已改为 md5 格式，无法逆向解析，通过查询 Redis 缓存获取
     */
    private Integer parseRoomIdFromStreamKey(String streamKey) {
        if (!StringUtils.hasText(streamKey)) {
            return null;
        }
        // streamKey 格式: live_{md5}，遍历所有房间查缓存匹配（规模小可用）
        // 更好的方案：streamKey = roomId + "_" + md5(anchorId_secret)，此处简化处理
        return extractRoomIdFromStreamKey(streamKey);
    }

    /**
     * 兼容旧格式 live_{roomId}_{anchorId}，也支持新格式 live_{md5}
     */
    private Integer extractRoomIdFromStreamKey(String streamKey) {
        if (streamKey == null || streamKey.isEmpty()) {
            return null;
        }
        if (streamKey.startsWith("live_")) {
            String rest = streamKey.substring(5);
            // 尝试解析新格式 md5 (32字符) 或旧格式 live_{roomId}_{anchorId}
            if (rest.length() == 32) {
                // 新格式: 遍历缓存查找对应 roomId（轻量实现）
                return findRoomIdByStreamKey(streamKey);
            }
            // 旧格式兼容
            String[] parts = rest.split("_");
            if (parts.length >= 2) {
                try {
                    return Integer.parseInt(parts[0]);
                } catch (NumberFormatException e) {
                    LOGGER.error("parse roomId failed, streamKey={}", streamKey, e);
                }
            }
        }
        return null;
    }

    /**
     * 根据 streamKey 查找对应的 roomId（通过 Redis 缓存）
     */
    private Integer findRoomIdByStreamKey(String streamKey) {
        // 实际生产中建议用 Redis SCAN 遍历，或在 Redis 中反向存一份 streamKey->roomId 的映射
        // 此处简化：直接查询数据库（假设房间数量有限）
        var rooms = livingRoomMapper.selectList(null);
        for (LivingRoomPO room : rooms) {
            String cached = (String) redisTemplate.opsForValue().get(cacheKeyBuilder.buildStreamKey(room.getId()));
            if (streamKey.equals(cached)) {
                return room.getId();
            }
        }
        return null;
    }

    /**
     * 异步修正流状态到数据库
     */
    @Async
    public void fixStreamStatusAsync(Integer roomId, String streamKey, int status) {
        try {
            livingRoomMapper.updateStreamStatus(roomId, status, null);
            LOGGER.info("[fixStreamStatusAsync] roomId={}, status={}", roomId, status);
        } catch (Exception e) {
            LOGGER.error("[fixStreamStatusAsync] failed, roomId={}", roomId, e);
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }
}
