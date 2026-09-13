package org.qiyu.live.video.provider.config;

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
 * 视频 MinIO 访问配置（与 api 侧 VideoMinioConfig 同配置：转码产物上传）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "qiyu.minio")
public class VideoMinioConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoMinioConfig.class);

    private static final String PUBLIC_READ_POLICY =
            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                    + "\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}";

    private String endpoint = "http://127.0.0.1:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String videoBucket = "qiyu-live-videos";
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

    /** 对象内部下载地址（endpoint 直连，绕开 dev 的 vite 代理） */
    public String internalUrl(String objectName) {
        return endpoint + "/" + videoBucket + "/" + objectName;
    }

    /** 对象外部访问 URL */
    public String publicUrl(String objectName) {
        String base = StringUtils.hasText(publicEndpoint) ? publicEndpoint : endpoint;
        return base + "/" + videoBucket + "/" + objectName;
    }
}
