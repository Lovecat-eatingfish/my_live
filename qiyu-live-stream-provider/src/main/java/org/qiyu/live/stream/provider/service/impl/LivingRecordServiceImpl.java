package org.qiyu.live.stream.provider.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.qiyu.live.im.router.interfaces.constants.ImMsgBizCodeEnum;
import org.qiyu.live.stream.provider.config.SrsConfig;
import org.qiyu.live.stream.provider.config.StreamProviderCacheKeyBuilder;
import org.qiyu.live.stream.provider.dao.mapper.LivingRoomMapper;
import org.qiyu.live.stream.provider.dao.mapper.LivingRoomRecordMapper;
import org.qiyu.live.stream.provider.dao.po.LivingRoomPO;
import org.qiyu.live.stream.provider.dao.po.LivingRoomRecordPO;
import org.qiyu.live.stream.provider.service.IImBroadcastService;
import org.qiyu.live.stream.provider.service.ILivingRecordService;
import org.qiyu.live.stream.provider.service.IMinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 直播录制回放服务实现
 * <p>
 * 完整链路：主播推流 -> SRS DVR 录制为 MP4 -> 关播后 SRS on_dvr 回调 ->
 * 上传 MinIO -> 写入 t_living_room_record -> IM 5564 广播回放可用
 */
@Service
public class LivingRecordServiceImpl implements ILivingRecordService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivingRecordServiceImpl.class);

    private static final int RECORD_STATUS_AVAILABLE = 2;
    private static final int RECORD_STATUS_FAILED = 3;
    private static final long CONTEXT_TTL_HOURS = 2;

    @Resource
    private LivingRoomMapper livingRoomMapper;
    @Resource
    private LivingRoomRecordMapper livingRoomRecordMapper;
    @Resource
    private SrsConfig srsConfig;
    @Resource
    private StreamProviderCacheKeyBuilder cacheKeyBuilder;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IMinioStorageService minioStorageService;
    @Resource
    private IImBroadcastService imBroadcastService;

    /** 上传 MinIO 可能耗时较长，不能阻塞 SRS 回调 HTTP 线程，单独开线程池执行 */
    private final ExecutorService uploadExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "record-upload-");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void captureStreamContext(Integer roomId, String streamKey) {
        if (roomId == null || !StringUtils.hasText(streamKey)) {
            return;
        }
        try {
            LivingRoomPO room = livingRoomMapper.selectById(roomId);
            if (room == null) {
                return;
            }
            JSONObject context = new JSONObject();
            context.put("roomId", roomId);
            context.put("anchorId", room.getAnchorId());
            context.put("streamKey", streamKey);
            context.put("recordEnabled", room.getRecordEnabled() != null && room.getRecordEnabled() == 1);
            Date startTime = room.getStreamStartTime() != null ? room.getStreamStartTime() : new Date();
            context.put("startTime", startTime.getTime());
            stringRedisTemplate.opsForValue().set(cacheKeyBuilder.buildRecordContextKey(streamKey),
                    context.toJSONString(), CONTEXT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            LOGGER.error("[captureStreamContext] failed, roomId={}, streamKey={}", roomId, streamKey, e);
        }
    }

    @Override
    public void handleDvrFile(String streamKey, String containerFile) {
        LOGGER.info("[handleDvrFile] streamKey={}, file={}", streamKey, containerFile);
        JSONObject context = resolveRecordContext(streamKey);
        if (context == null) {
            LOGGER.warn("[handleDvrFile] no record context for streamKey={}, skip", streamKey);
            return;
        }
        String localPath = toLocalPath(containerFile);
        if (localPath == null) {
            LOGGER.warn("[handleDvrFile] cannot map container path to local: {}", containerFile);
            return;
        }
        // 未开启录制的房间：直接清理本地文件
        if (!context.getBooleanValue("recordEnabled")) {
            deleteQuietly(localPath);
            LOGGER.info("[handleDvrFile] record disabled for room {}, file removed", context.getInteger("roomId"));
            return;
        }
        File localFile = new File(localPath);
        if (!localFile.exists() || localFile.length() == 0) {
            LOGGER.warn("[handleDvrFile] dvr file not found: {}", localPath);
            return;
        }
        uploadExecutor.execute(() -> doUploadAndSave(context, localFile));
    }

    /**
     * 上传 MinIO -> 写回放记录 -> IM 5564 广播 -> 清理本地文件
     */
    private void doUploadAndSave(JSONObject context, File localFile) {
        Integer roomId = context.getInteger("roomId");
        Long anchorId = context.getLong("anchorId");
        Long startTime = context.getLongValue("startTime");
        long endTime = System.currentTimeMillis();
        try {
            String objectName = new SimpleDateFormat("yyyy/MM/dd").format(new Date(endTime))
                    + "/" + roomId + "_" + anchorId + "_" + endTime + ".flv";
            String recordUrl = minioStorageService.uploadRecordFile(localFile.getAbsolutePath(), objectName);

            LivingRoomRecordPO recordPO = new LivingRoomRecordPO();
            recordPO.setRoomId(roomId);
            recordPO.setAnchorId(anchorId);
            recordPO.setRecordUrl(recordUrl);
            recordPO.setDuration((int) Math.max(0, (endTime - startTime) / 1000));
            recordPO.setFileSize(localFile.length());
            recordPO.setStartTime(new Date(startTime));
            recordPO.setEndTime(new Date(endTime));
            recordPO.setStatus(RECORD_STATUS_AVAILABLE);
            recordPO.setCreateTime(new Date());
            livingRoomRecordMapper.insert(recordPO);

            JSONObject notify = new JSONObject();
            notify.put("roomId", roomId);
            notify.put("recordUrl", recordUrl);
            imBroadcastService.broadcastToRoom(roomId, ImMsgBizCodeEnum.LIVING_RECORD_DONE, notify);
            LOGGER.info("[doUploadAndSave] record saved, roomId={}, url={}", roomId, recordUrl);
        } catch (Exception e) {
            LOGGER.error("[doUploadAndSave] failed, roomId={}, file={}", roomId, localFile.getAbsolutePath(), e);
            saveFailedRecord(roomId, anchorId, startTime, endTime, localFile.length());
        } finally {
            deleteQuietly(localFile.getAbsolutePath());
        }
    }

    private void saveFailedRecord(Integer roomId, Long anchorId, Long startTime, long endTime, long fileSize) {
        try {
            LivingRoomRecordPO recordPO = new LivingRoomRecordPO();
            recordPO.setRoomId(roomId);
            recordPO.setAnchorId(anchorId);
            recordPO.setRecordUrl("");
            recordPO.setDuration((int) Math.max(0, (endTime - startTime) / 1000));
            recordPO.setFileSize(fileSize);
            recordPO.setStartTime(new Date(startTime));
            recordPO.setEndTime(new Date(endTime));
            recordPO.setStatus(RECORD_STATUS_FAILED);
            recordPO.setCreateTime(new Date());
            livingRoomRecordMapper.insert(recordPO);
        } catch (Exception ex) {
            LOGGER.error("[saveFailedRecord] failed, roomId={}", roomId, ex);
        }
    }

    /**
     * 解析录制上下文：优先 Redis 暂存（关播清理 DB 前已写入），未命中再按 stream_key 查 DB
     */
    private JSONObject resolveRecordContext(String streamKey) {
        String cached = stringRedisTemplate.opsForValue().get(cacheKeyBuilder.buildRecordContextKey(streamKey));
        if (cached != null) {
            return JSON.parseObject(cached);
        }
        LivingRoomPO room = livingRoomMapper.selectOne(
                new LambdaQueryWrapper<LivingRoomPO>()
                        .eq(LivingRoomPO::getStreamKey, streamKey)
                        .last("limit 1"));
        if (room == null) {
            return null;
        }
        JSONObject context = new JSONObject();
        context.put("roomId", room.getId());
        context.put("anchorId", room.getAnchorId());
        context.put("streamKey", streamKey);
        context.put("recordEnabled", room.getRecordEnabled() != null && room.getRecordEnabled() == 1);
        context.put("startTime", room.getStreamStartTime() != null
                ? room.getStreamStartTime().getTime() : System.currentTimeMillis());
        return context;
    }

    /**
     * SRS 容器内路径 -> 宿主机本地路径
     * /data/dvr/live/xxx.mp4 -> ./docker-data/srs/dvr/live/xxx.mp4
     */
    private String toLocalPath(String containerFile) {
        String prefix = srsConfig.getDvrContainerPrefix();
        if (!StringUtils.hasText(containerFile) || !containerFile.startsWith(prefix)) {
            return null;
        }
        String suffix = containerFile.substring(prefix.length());
        if (suffix.startsWith("/") || suffix.startsWith("\\")) {
            suffix = suffix.substring(1);
        }
        String localDir = srsConfig.getDvrLocalDir();
        return (localDir.endsWith("/") || localDir.endsWith("\\")
                ? localDir : localDir + "/") + suffix;
    }

    private void deleteQuietly(String path) {
        try {
            Files.deleteIfExists(new File(path).toPath());
        } catch (Exception e) {
            LOGGER.warn("[deleteQuietly] failed, path={}", path, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        uploadExecutor.shutdown();
    }
}
