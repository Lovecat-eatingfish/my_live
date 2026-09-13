package org.qiyu.live.stream.provider.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.stream.interfaces.dto.LivingStreamPushUrlDTO;
import org.qiyu.live.stream.interfaces.dto.StreamStatusDTO;
import org.qiyu.live.stream.provider.config.SrsConfig;
import org.qiyu.live.stream.provider.config.StreamProviderCacheKeyBuilder;
import org.qiyu.live.stream.provider.dao.mapper.LivingRoomMapper;
import org.qiyu.live.stream.provider.dao.po.LivingRoomPO;
import org.qiyu.live.stream.provider.service.IImBroadcastService;
import org.qiyu.live.stream.provider.service.ILivingRecordService;
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
    /** 开播宽限期：SRS 注册流（WebRTC 握手/桥接）需要数秒，期间不做断流误判 */
    private static final long GRACE_PERIOD_MS = 30_000L;

    /** 推流地址缓存时长，与 streamKey 有效期一致 */
    private static final long STREAM_KEY_EXPIRE_HOURS = 24;

    @Resource
    private LivingRoomMapper livingRoomMapper;
    @Resource
    private SrsConfig srsConfig;
    @Resource
    private StreamProviderCacheKeyBuilder cacheKeyBuilder;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISrsApiService srsApiService;
    @Resource
    private ILivingRecordService livingRecordService;
    @Resource
    private IImBroadcastService imBroadcastService;

    @Override
    public LivingStreamPushUrlDTO createGuestPushUrl(Integer roomId, Long guestUserId) {
        String raw = roomId + "_" + guestUserId + "_" + srsConfig.getSecret();
        String streamKey = "liveg_" + md5(raw);
        String pushUrl = srsConfig.getRtmpBaseUrl() + "/" + streamKey;
        // 反向映射供 SRS on_publish 回调反查 roomId（正向映射与房间主 key 分开，互不影响）
        stringRedisTemplate.opsForValue().set(cacheKeyBuilder.buildStreamKeyReverse(streamKey),
                String.valueOf(roomId), STREAM_KEY_EXPIRE_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.opsForValue().set(cacheKeyBuilder.buildGuestStreamKey(roomId, guestUserId),
                streamKey, STREAM_KEY_EXPIRE_HOURS, TimeUnit.HOURS);
        LivingStreamPushUrlDTO dto = new LivingStreamPushUrlDTO();
        dto.setPushUrl(pushUrl);
        dto.setStreamKey(streamKey);
        dto.setExpireTime(System.currentTimeMillis() + STREAM_KEY_EXPIRE_HOURS * 3600 * 1000L);
        dto.setRtcPublishApi(srsConfig.getRtcPublishApiUrl());
        dto.setRtcStreamUrl(srsConfig.getRtcStreamBaseUrl() + "/" + streamKey);
        dto.setHlsUrl(srsConfig.getHlsBaseUrl() + "/" + streamKey + ".m3u8");
        LOGGER.info("[createGuestPushUrl] roomId={}, guestUserId={}, streamKey={}", roomId, guestUserId, streamKey);
        return dto;
    }

    @Override
    public LivingStreamPushUrlDTO createPushUrl(Integer roomId, Long anchorId) {
        // 1. 生成带签名的 streamKey，防伪造
        String streamKey = buildStreamKey(roomId, anchorId);
        // 2. 拼接推流地址
        String pushUrl = srsConfig.getRtmpBaseUrl() + "/" + streamKey;

        // 3. 存储 streamKey 到数据库
        livingRoomMapper.updateStreamKey(roomId, streamKey, pushUrl);

        // 4. 缓存到 Redis（正向: roomId->streamKey，反向: streamKey->roomId 供 SRS 回调反查）
        stringRedisTemplate.opsForValue().set(cacheKeyBuilder.buildStreamKey(roomId),
                streamKey, STREAM_KEY_EXPIRE_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.opsForValue().set(cacheKeyBuilder.buildStreamKeyReverse(streamKey),
                String.valueOf(roomId), STREAM_KEY_EXPIRE_HOURS, TimeUnit.HOURS);

        // 5. 返回结果
        LivingStreamPushUrlDTO dto = new LivingStreamPushUrlDTO();
        dto.setPushUrl(pushUrl);
        dto.setStreamKey(streamKey);
        dto.setExpireTime(System.currentTimeMillis() + STREAM_KEY_EXPIRE_HOURS * 3600 * 1000L);
        // WebRTC 浏览器开播地址
        dto.setRtcPublishApi(srsConfig.getRtcPublishApiUrl());
        dto.setRtcStreamUrl(srsConfig.getRtcStreamBaseUrl() + "/" + streamKey);
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
        int status = dbStatus != null ? dbStatus : STREAM_STATUS_NOT_START;
        dto.setStatus(status);

        // 2. 如果是推流中，去 SRS 校验真实情况，发现流已断则修正
        if (STREAM_STATUS_LIVING == status) {
            String streamKey = room.getStreamKey();
            if (StringUtils.hasText(streamKey)) {
                boolean online = srsApiService.isStreamOnline(streamKey);
                if (!online) {
                    // 刚开播的宽限期内 SRS 还没注册流（WebRTC 握手/rtc→rtmp 桥接需要数秒），
                    // 不能误判为断流，否则主播页面的状态轮询会把 DB 状态打成"异常"卡死
                    Date start = room.getStreamStartTime();
                    boolean inGracePeriod = start != null
                            && System.currentTimeMillis() - start.getTime() < GRACE_PERIOD_MS;
                    if (inGracePeriod) {
                        dto.setViewerCount(0);
                        return dto;
                    }
                    dto.setStatus(STREAM_STATUS_ERROR);
                    dto.setViewerCount(0);
                    fixStreamStatus(roomId, STREAM_STATUS_ERROR);
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
        // 关播清理前暂存录制上下文，供稍后到达的 on_dvr 回调使用
        livingRecordService.captureStreamContext(roomId, streamKey);
        // 主动关播时把还在推流的客户端踢掉，防止关播后流还在
        if (StringUtils.hasText(streamKey)) {
            try {
                int kicked = srsApiService.kickPublishClients(streamKey);
                LOGGER.info("[stopStream] kick publish clients, roomId={}, kicked={}", roomId, kicked);
            } catch (Exception e) {
                LOGGER.warn("[stopStream] kick publish clients failed, roomId={}", roomId, e);
            }
        }
        // 重置数据库流状态
        livingRoomMapper.resetStream(roomId);
        // 清除 Redis 缓存
        stringRedisTemplate.delete(cacheKeyBuilder.buildStreamKey(roomId));
        stringRedisTemplate.delete(cacheKeyBuilder.buildStreamStatus(roomId));
        if (StringUtils.hasText(streamKey)) {
            stringRedisTemplate.delete(cacheKeyBuilder.buildStreamKeyReverse(streamKey));
        }
        // IM 5563: 通知房间观众推流已结束
        JSONObject notify = new JSONObject();
        notify.put("roomId", roomId);
        notify.put("status", STREAM_STATUS_NOT_START);
        imBroadcastService.broadcastToRoom(roomId, ImMsgBizCodeEnum.LIVING_STREAM_STATUS_CHANGE, notify);
        LOGGER.info("[stopStream] roomId={}, streamKey={}", roomId, streamKey);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPublish(String streamKey, String clientId, String ip) {
        LOGGER.info("[onPublish] streamKey={}, clientId={}, ip={}", streamKey, clientId, ip);
        // 1. 从 streamKey 反查出 roomId
        Integer roomId = parseRoomIdFromStreamKey(streamKey);
        if (roomId == null) {
            LOGGER.warn("[onPublish] cannot resolve roomId from streamKey={}", streamKey);
            return;
        }
        // 2. 校验 streamKey 归属（防止伪造推流）
        String cachedKey = stringRedisTemplate.opsForValue().get(cacheKeyBuilder.buildStreamKey(roomId));
        if (!streamKey.equals(cachedKey)) {
            LOGGER.warn("[onPublish] streamKey mismatch, roomId={}, expected={}, got={}",
                    roomId, cachedKey, streamKey);
            return;
        }
        // 3. 更新数据库流状态
        livingRoomMapper.updateStreamStatus(roomId, STREAM_STATUS_LIVING, new Date());
        // 4. 暂存录制上下文（本段直播的起始时间等），on_dvr 回调上传 MinIO 时使用
        livingRecordService.captureStreamContext(roomId, streamKey);
        // 5. 缓存状态到 Redis
        stringRedisTemplate.opsForValue().set(cacheKeyBuilder.buildStreamStatus(roomId),
                String.valueOf(STREAM_STATUS_LIVING), STREAM_KEY_EXPIRE_HOURS, TimeUnit.HOURS);
        // 6. IM 5563: 通知房间观众推流已开始（观众端可自动起播）
        JSONObject notify = new JSONObject();
        notify.put("roomId", roomId);
        notify.put("status", STREAM_STATUS_LIVING);
        imBroadcastService.broadcastToRoom(roomId, ImMsgBizCodeEnum.LIVING_STREAM_STATUS_CHANGE, notify);
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
        stringRedisTemplate.delete(cacheKeyBuilder.buildStreamStatus(roomId));
        // IM 5563: 主播直接停 OBS 未点关播时，也通知观众推流已结束
        JSONObject notify = new JSONObject();
        notify.put("roomId", roomId);
        notify.put("status", STREAM_STATUS_NOT_START);
        imBroadcastService.broadcastToRoom(roomId, ImMsgBizCodeEnum.LIVING_STREAM_STATUS_CHANGE, notify);
    }

    /**
     * 生成带签名的 streamKey，防止伪造
     * 格式: live_{md5(roomId_anchorId_secret)}
     */
    private String buildStreamKey(Integer roomId, Long anchorId) {
        String raw = roomId + "_" + anchorId + "_" + srsConfig.getSecret();
        return "live_" + md5(raw);
    }

    /**
     * 从 streamKey 解析 roomId
     * 优先走 Redis 反向映射（createPushUrl 时写入），未命中再查 DB 中的 stream_key 字段
     */
    private Integer parseRoomIdFromStreamKey(String streamKey) {
        if (!StringUtils.hasText(streamKey)) {
            return null;
        }
        // 1. Redis 反向映射
        String cachedRoomId = stringRedisTemplate.opsForValue().get(cacheKeyBuilder.buildStreamKeyReverse(streamKey));
        if (cachedRoomId != null) {
            try {
                return Integer.parseInt(cachedRoomId);
            } catch (NumberFormatException e) {
                LOGGER.warn("[parseRoomIdFromStreamKey] invalid roomId cache: {}", cachedRoomId);
            }
        }
        // 2. DB 兜底（Redis 缓存过期但推流地址仍有效期内）
        LivingRoomPO room = livingRoomMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LivingRoomPO>()
                        .eq(LivingRoomPO::getStreamKey, streamKey)
                        .last("limit 1"));
        return room != null ? room.getId() : null;
    }

    /**
     * 修正流状态到数据库（单条更新，同步执行即可）
     */
    private void fixStreamStatus(Integer roomId, int status) {
        try {
            livingRoomMapper.updateStreamStatus(roomId, status, null);
            stringRedisTemplate.delete(cacheKeyBuilder.buildStreamStatus(roomId));
            LOGGER.info("[fixStreamStatus] roomId={}, status={}", roomId, status);
        } catch (Exception e) {
            LOGGER.error("[fixStreamStatus] failed, roomId={}", roomId, e);
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
