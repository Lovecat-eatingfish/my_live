package org.qiyu.live.video.provider.service.impl;

import com.alibaba.fastjson.JSONObject;
import io.minio.PutObjectArgs;
import jakarta.annotation.Resource;
import org.qiyu.live.video.provider.config.VideoMinioConfig;
import org.qiyu.live.video.provider.dao.maper.IVideoInfoMapper;
import org.qiyu.live.video.provider.dao.po.VideoInfoPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 轻量异步转码（§6.13）：
 * ① ffprobe 探测真实编码/时长 → ② H.264+AAC mp4 走 -c copy 秒级 remux（修进度条拖动），
 * 其他编码走 libx264 veryfast crf26 720p 重编码 → ③ 无封面时 -ss 1 抽帧 → ④ 传 MinIO
 * videos/transcoded/（原文件保留），回写 t_video_info。
 * 幂等：t_video_info.transcode_status==1 直接跳过（单实例消费，DB 状态即幂等）。
 */
@Service
public class VideoTranscodeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoTranscodeService.class);

    private static final int PROC_TIMEOUT_SECONDS = 600;

    @Resource
    private IVideoInfoMapper videoInfoMapper;
    @Resource
    private VideoMinioConfig videoMinioConfig;

    /** 转码状态：完成 */
    public static final int TS_DONE = 1;
    /** 转码状态：失败（回退播原文件） */
    public static final int TS_FAILED = 2;
    /** 转码状态：处理中 */
    public static final int TS_PROCESSING = 0;

    public void transcode(Long videoId) throws Exception {
        VideoInfoPO po = videoInfoMapper.selectById(videoId);
        if (po == null) {
            LOGGER.warn("[transcode] video not found, videoId={}", videoId);
            return;
        }
        if (po.getTranscodeStatus() != null && po.getTranscodeStatus() == TS_DONE) {
            return;
        }
        VideoInfoPO processing = new VideoInfoPO();
        processing.setId(videoId);
        processing.setTranscodeStatus(TS_PROCESSING);
        videoInfoMapper.updateById(processing);

        String sourceUrl = po.getVideoUrl();
        Path workDir = Files.createTempDirectory("video-transcode-" + videoId + "-");
        try {
            File srcFile = download(sourceUrl, workDir.resolve("src" + suffix(sourceUrl)).toFile());

            JSONObject probe = ffprobe(srcFile);
            String videoCodec = getCodec(probe, "video");
            String audioCodec = getCodec(probe, "audio");
            String container = probe.getJSONObject("format") != null
                    ? probe.getJSONObject("format").getString("format_name") : "";
            double durationSec = probe.getJSONObject("format") != null
                    ? probe.getJSONObject("format").getDoubleValue("duration") : 0;

            boolean fastRemux = "h264".equalsIgnoreCase(videoCodec)
                    && ("aac".equalsIgnoreCase(audioCodec) || audioCodec == null)
                    && container != null && container.contains("mp4");

            File outFile = workDir.resolve("transcoded.mp4").toFile();
            if (fastRemux) {
                run(new String[]{"ffmpeg", "-y", "-i", srcFile.getAbsolutePath(),
                        "-c", "copy", "-movflags", "+faststart", outFile.getAbsolutePath()});
            } else {
                LOGGER.info("[transcode] re-encoding videoId={} videoCodec={} audioCodec={} container={}",
                        videoId, videoCodec, audioCodec, container);
                run(new String[]{"ffmpeg", "-y", "-i", srcFile.getAbsolutePath(),
                        "-vf", "scale=-2:720",
                        "-c:v", "libx264", "-preset", "veryfast", "-crf", "26",
                        "-c:a", "aac", outFile.getAbsolutePath()});
            }

            // 封面：用户传过则跳过
            boolean needCover = po.getCoverUrl() == null || po.getCoverUrl().isEmpty();
            File coverFile = null;
            if (needCover) {
                coverFile = workDir.resolve("cover.jpg").toFile();
                run(new String[]{"ffmpeg", "-y", "-ss", "1", "-i", outFile.getAbsolutePath(),
                        "-frames:v", "1", "-q:v", "3", coverFile.getAbsolutePath()});
            }

            long newSize = outFile.length();
            String outObjectName = "videos/transcoded/" + videoId + "_" + System.currentTimeMillis() + ".mp4";
            upload(outFile, outObjectName);
            String newUrl = videoMinioConfig.publicUrl(outObjectName);

            VideoInfoPO update = new VideoInfoPO();
            update.setId(videoId);
            update.setVideoUrl(newUrl);
            update.setDuration((int) Math.round(durationSec));
            update.setSize(newSize);
            update.setTranscodeStatus(TS_DONE);
            if (coverFile != null && coverFile.exists()) {
                String coverObject = "videos/covers/" + videoId + ".jpg";
                upload(coverFile, coverObject);
                update.setCoverUrl(videoMinioConfig.publicUrl(coverObject));
            }
            videoInfoMapper.updateById(update);
            LOGGER.info("[transcode] done videoId={} remux={} duration={}s size={}",
                    videoId, fastRemux, (int) Math.round(durationSec), newSize);
        } finally {
            deleteQuietly(workDir.toFile());
        }
    }

    /** 重试耗尽后置失败态（回退播原 video_url，不改 url） */
    public void markFailed(Long videoId) {
        VideoInfoPO update = new VideoInfoPO();
        update.setId(videoId);
        update.setTranscodeStatus(TS_FAILED);
        videoInfoMapper.updateById(update);
        LOGGER.warn("[transcode] markFailed videoId={}", videoId);
    }

    private JSONObject ffprobe(File file) throws Exception {
        String[] cmd = {"ffprobe", "-v", "quiet", "-print_format", "json", "-show_streams", "-show_format",
                file.getAbsolutePath()};
        String json = runCapture(cmd);
        return JSONObject.parseObject(json);
    }

    private String getCodec(JSONObject probe, String type) {
        if (probe == null || probe.getJSONArray("streams") == null) {
            return null;
        }
        return probe.getJSONArray("streams").stream()
                .map(o -> (JSONObject) o)
                .filter(s -> type.equals(s.getString("codec_type")))
                .map(s -> s.getString("codec_name"))
                .findFirst().orElse(null);
    }

    private String runCapture(String[] cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out;
        try (InputStream in = proc.getInputStream()) {
            out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!proc.waitFor(PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS) || proc.exitValue() != 0) {
            throw new IllegalStateException("process failed: " + cmd[0] + " exit=" + proc.exitValue()
                    + " out=" + out.substring(0, Math.min(out.length(), 500)));
        }
        return out;
    }

    private void run(String[] cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output;
        try (InputStream in = proc.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!proc.waitFor(PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS) || proc.exitValue() != 0) {
            proc.destroyForcibly();
            throw new IllegalStateException("process failed: " + cmd[0]
                    + " out=" + output.substring(0, Math.min(output.length(), 800)));
        }
    }

    private File download(String urlStr, File target) throws Exception {
        // publicEndpoint 走 dev 的 vite 代理可能不可达，优先换回内部 endpoint 直连
        String internal = videoMinioConfig.internalUrl(objectNameOf(urlStr));
        HttpURLConnection conn = (HttpURLConnection) new URL(internal).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(120_000);
        int code = conn.getResponseCode();
        if (code != 200) {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(120_000);
        }
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /** 从完整 URL 提取 MinIO 对象名（{bucket}/ 之后的部分，兼容 dev 的 /minio 代理前缀） */
    private String objectNameOf(String url) {
        String path = URLDecoder.decode(url, StandardCharsets.UTF_8);
        int bucketIdx = path.indexOf("/" + videoMinioConfig.getVideoBucket() + "/");
        if (bucketIdx < 0) {
            throw new IllegalArgumentException("not a video bucket url: " + url);
        }
        return path.substring(bucketIdx + videoMinioConfig.getVideoBucket().length() + 2);
    }

    private void upload(File file, String objectName) throws Exception {
        try (InputStream in = new FileInputStream(file)) {
            videoMinioConfig.getMinioClient().putObject(PutObjectArgs.builder()
                    .bucket(videoMinioConfig.getVideoBucket())
                    .object(objectName)
                    .stream(in, file.length(), -1)
                    .build());
        }
    }

    private String suffix(String url) {
        String clean = url.split("\\?")[0].toLowerCase();
        if (clean.endsWith(".webm")) return ".webm";
        if (clean.endsWith(".mov")) return ".mov";
        return ".mp4";
    }

    private void deleteQuietly(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        dir.delete();
    }
}
