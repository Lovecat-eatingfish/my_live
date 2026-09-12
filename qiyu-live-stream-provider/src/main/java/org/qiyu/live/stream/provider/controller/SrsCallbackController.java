package org.qiyu.live.stream.provider.controller;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.qiyu.live.stream.provider.service.ILivingRecordService;
import org.qiyu.live.stream.provider.service.ILivingStreamService;

/**
 * SRS 回调接口
 * SRS 在推流开始/结束时通过 HTTP 回调通知此接口
 */
@RestController
@RequestMapping("/api/stream")
public class SrsCallbackController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SrsCallbackController.class);

    /** SRS 4 的 http_hooks 要求返回 JSON，code=0 表示允许，非 0 表示拒绝 */
    private static final String SRS_OK = "{\"code\":0}";
    private static final String SRS_REJECT = "{\"code\":1}";

    /** SRS 回调密钥，与 SRS 配置中的 secret 一致 */
    @Value("${qiyu.srs.callback-secret:}")
    private String callbackSecret;

    @Resource
    private ILivingStreamService livingStreamService;
    @Resource
    private ILivingRecordService livingRecordService;

    /**
     * SRS 推流开始回调
     * 需要在 SRS 配置中启用: publish 2k rich_hints;
     * 回调地址: http://your-server:9092/api/stream/on_publish
     */
    @PostMapping("/on_publish")
    public String onPublish(@RequestBody SrsCallbackVO vo,
                            @RequestHeader(value = "X-Srs-Secret", required = false) String secret) {
        LOGGER.info("[SRS on_publish] stream={}, client_id={}, ip={}",
                vo.getStream(), vo.getClient_id(), vo.getIp());
        if (!validateSecret(secret)) {
            LOGGER.warn("[SRS on_publish] unauthorized, secret mismatch");
            return SRS_REJECT;
        }
        try {
            livingStreamService.onPublish(vo.getStream(), vo.getClient_id(), vo.getIp());
            return SRS_OK;
        } catch (Exception e) {
            LOGGER.error("[SRS on_publish] error", e);
            return SRS_REJECT;
        }
    }

    /**
     * SRS 推流结束回调
     */
    @PostMapping("/on_unpublish")
    public String onUnpublish(@RequestBody SrsCallbackVO vo,
                              @RequestHeader(value = "X-Srs-Secret", required = false) String secret) {
        LOGGER.info("[SRS on_unpublish] stream={}", vo.getStream());
        if (!validateSecret(secret)) {
            LOGGER.warn("[SRS on_unpublish] unauthorized, secret mismatch");
            return SRS_REJECT;
        }
        try {
            livingStreamService.onUnpublish(vo.getStream());
            return SRS_OK;
        } catch (Exception e) {
            LOGGER.error("[SRS on_unpublish] error", e);
            return SRS_REJECT;
        }
    }

    /**
     * SRS DVR 录制完成回调（dvr_plan=session 时关播后文件落盘触发）
     * 触发录制文件上传 MinIO 并写入回放记录
     */
    @PostMapping("/on_dvr")
    public String onDvr(@RequestBody SrsCallbackVO vo,
                        @RequestHeader(value = "X-Srs-Secret", required = false) String secret) {
        LOGGER.info("[SRS on_dvr] stream={}, file={}", vo.getStream(), vo.getFile());
        if (!validateSecret(secret)) {
            LOGGER.warn("[SRS on_dvr] unauthorized, secret mismatch");
            return SRS_REJECT;
        }
        try {
            livingRecordService.handleDvrFile(vo.getStream(), vo.getFile());
            return SRS_OK;
        } catch (Exception e) {
            LOGGER.error("[SRS on_dvr] error", e);
            return SRS_OK; // 录制处理失败不影响 SRS 自身流程
        }
    }

    /**
     * 校验回调密钥
     */
    private boolean validateSecret(String secret) {
        if (callbackSecret == null || callbackSecret.isEmpty()) {
            // 未配置密钥时跳过校验（开发环境）
            return true;
        }
        return callbackSecret.equals(secret);
    }

    /**
     * SRS 回调 VO
     */
    public static class SrsCallbackVO {
        private String action;
        private String stream;
        private String client_id;
        private String ip;
        private Long timestamp;
        /** on_dvr 回调：容器内录制文件路径 */
        private String file;

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getStream() { return stream; }
        public void setStream(String stream) { this.stream = stream; }
        public String getClient_id() { return client_id; }
        public void setClient_id(String client_id) { this.client_id = client_id; }
        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
    }
}
