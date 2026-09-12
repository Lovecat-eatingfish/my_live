package org.qiyu.live.stream.provider.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置（直播录制回放）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "qiyu.minio")
public class MinioConfig {

    /** MinIO S3 API 地址 */
    private String endpoint = "http://127.0.0.1:9000";

    /** 访问密钥 */
    private String accessKey = "minioadmin";

    /** 秘密密钥 */
    private String secretKey = "minioadmin";

    /** 录制文件桶名 */
    private String bucket = "qiyu-live-records";

    /**
     * 浏览器访问回放文件的地址前缀（默认同 endpoint）
     * MinIO 部署在容器/内网时浏览器可达地址可能不同，可单独指定
     */
    private String publicEndpoint;
}
