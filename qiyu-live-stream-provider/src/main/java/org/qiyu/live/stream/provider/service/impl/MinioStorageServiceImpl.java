package org.qiyu.live.stream.provider.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.UploadObjectArgs;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import org.qiyu.live.stream.provider.config.MinioConfig;
import org.qiyu.live.stream.provider.service.IMinioStorageService;

/**
 * MinIO 对象存储服务实现
 */
@Service
public class MinioStorageServiceImpl implements IMinioStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinioStorageServiceImpl.class);

    /** 桶公开读策略，回放文件浏览器直链播放 */
    private static final String PUBLIC_READ_POLICY =
            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},"
                    + "\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}";

    @Resource
    private MinioConfig minioConfig;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        this.minioClient = MinioClient.builder()
                .endpoint(minioConfig.getEndpoint())
                .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                .build();
        ensureBucketReady();
    }

    @Override
    public void ensureBucketReady() {
        String bucket = minioConfig.getBucket();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                LOGGER.info("[ensureBucketReady] bucket {} created", bucket);
            }
            // 设置桶内对象可匿名 GET，保证回放 URL 能直接在浏览器播放
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(String.format(PUBLIC_READ_POLICY, bucket))
                    .build());
        } catch (Exception e) {
            LOGGER.error("[ensureBucketReady] failed, endpoint={}, bucket={}", minioConfig.getEndpoint(), bucket, e);
        }
    }

    @Override
    public String uploadRecordFile(String localFilePath, String objectName) {
        try {
            minioClient.uploadObject(UploadObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .filename(localFilePath)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("upload record file to minio failed: " + localFilePath, e);
        }
        String base = StringUtils.hasText(minioConfig.getPublicEndpoint())
                ? minioConfig.getPublicEndpoint() : minioConfig.getEndpoint();
        return base + "/" + minioConfig.getBucket() + "/" + objectName;
    }
}
