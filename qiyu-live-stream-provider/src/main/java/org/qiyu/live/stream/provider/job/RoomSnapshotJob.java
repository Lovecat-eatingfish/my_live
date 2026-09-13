package org.qiyu.live.stream.provider.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.Resource;
import org.qiyu.live.stream.provider.config.MinioConfig;
import org.qiyu.live.stream.provider.config.SrsConfig;
import org.qiyu.live.stream.provider.dao.mapper.LivingRoomMapper;
import org.qiyu.live.stream.provider.dao.po.LivingRoomPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 直播间巡查截帧：每 30s 对推流中的房间用 ffmpeg 从 SRS 拉一帧，
 * 传 MinIO 并写 qiyu_live_common.risk_room_snapshot（跨库写入，admin 巡查页读取处置）。
 */
@Component
public class RoomSnapshotJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomSnapshotJob.class);

    /** 截帧目标桶：与视频封面同桶（公开读） */
    private static final String SNAPSHOT_BUCKET = "qiyu-live-videos";

    @Resource
    private LivingRoomMapper livingRoomMapper;
    @Resource
    private SrsConfig srsConfig;
    @Resource
    private MinioConfig minioConfig;
    @Autowired
    private DataSource dataSource;

    private volatile JdbcTemplate jdbcTemplate;
    private volatile MinioClient minioClient;

    @Scheduled(fixedDelay = 30_000)
    public void snapshot() {
        List<LivingRoomPO> rooms = livingRoomMapper.selectList(new LambdaQueryWrapper<LivingRoomPO>()
                .eq(LivingRoomPO::getStreamStatus, 1));
        if (rooms == null || rooms.isEmpty()) {
            return;
        }
        for (LivingRoomPO room : rooms) {
            if (room.getStreamKey() == null || room.getStreamKey().isEmpty()) {
                continue;
            }
            try {
                captureAndSave(room);
            } catch (Exception e) {
                LOGGER.warn("[snapshot] room {} failed: {}", room.getId(), e.getMessage());
            }
        }
    }

    private void captureAndSave(LivingRoomPO room) throws Exception {
        File tmp = Files.createTempFile("snapshot-" + room.getId() + "-", ".jpg").toFile();
        try {
            String rtmpUrl = srsConfig.getRtmpBaseUrl() + "/" + room.getStreamKey();
            Process proc = new ProcessBuilder("ffmpeg", "-y",
                    "-rw_timeout", "10000000",
                    "-i", rtmpUrl,
                    "-frames:v", "1", "-q:v", "5",
                    tmp.getAbsolutePath()).redirectErrorStream(true).start();
            proc.getInputStream().readAllBytes(); // 防缓冲区满阻塞
            if (!proc.waitFor(20, TimeUnit.SECONDS) || proc.exitValue() != 0) {
                proc.destroyForcibly();
                throw new IllegalStateException("ffmpeg exit=" + proc.exitValue());
            }
            if (tmp.length() < 1024) {
                throw new IllegalStateException("frame too small");
            }

            String objectName = "snapshots/" + room.getId() + "/" + System.currentTimeMillis() + ".jpg";
            getMinioClient().putObject(PutObjectArgs.builder()
                    .bucket(SNAPSHOT_BUCKET)
                    .object(objectName)
                    .stream(new FileInputStream(tmp), tmp.length(), -1)
                    .build());
            String baseUrl = minioConfig.getPublicEndpoint() != null && !minioConfig.getPublicEndpoint().isEmpty()
                    ? minioConfig.getPublicEndpoint() : minioConfig.getEndpoint();

            getJdbcTemplate().update(
                    "INSERT INTO qiyu_live_common.risk_room_snapshot (room_id, anchor_id, img_url) VALUES (?,?,?)",
                    room.getId(), room.getAnchorId(), baseUrl + "/" + SNAPSHOT_BUCKET + "/" + objectName);
            LOGGER.info("[snapshot] room {} captured", room.getId());
        } finally {
            tmp.delete();
        }
    }

    private MinioClient getMinioClient() {
        if (minioClient == null) {
            synchronized (this) {
                if (minioClient == null) {
                    minioClient = MinioClient.builder()
                            .endpoint(minioConfig.getEndpoint())
                            .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                            .build();
                }
            }
        }
        return minioClient;
    }

    private JdbcTemplate getJdbcTemplate() {
        if (jdbcTemplate == null) {
            synchronized (this) {
                if (jdbcTemplate == null) {
                    jdbcTemplate = new JdbcTemplate(dataSource);
                }
            }
        }
        return jdbcTemplate;
    }
}
