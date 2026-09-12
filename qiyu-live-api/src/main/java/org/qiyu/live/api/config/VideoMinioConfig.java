package org.qiyu.live.api.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 视频直存 MinIO 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "qiyu.minio")
public class VideoMinioConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoMinioConfig.class);

    /** 桶公开读策略（视频/封面浏览器直链播放） */
    private static final String PUBLIC_READ_POLICY =
            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                    + "\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}";

    private String endpoint = "http://127.0.0.1:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String videoBucket = "qiyu-live-videos";
    /** 对外访问基地址（dev 走 vite /minio 代理；生产为 nginx/gateway 地址），空则用 endpoint */
    private String publicEndpoint = "";

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureVideoBucketReady();
    }

    private void ensureVideoBucketReady() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(videoBucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(videoBucket).build());
                LOGGER.info("[ensureVideoBucketReady] bucket {} created", videoBucket);
            }
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(videoBucket)
                    .config(String.format(PUBLIC_READ_POLICY, videoBucket))
                    .build());
        } catch (Exception e) {
            LOGGER.error("[ensureVideoBucketReady] failed, endpoint={}, bucket={}", endpoint, videoBucket, e);
        }
    }

    /** 拼接对象的外部访问 URL */
    public String publicUrl(String objectName) {
        String base = StringUtils.hasText(publicEndpoint) ? publicEndpoint : endpoint;
        return base + "/" + videoBucket + "/" + objectName;
    }
}
