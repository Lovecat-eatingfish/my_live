package org.qiyu.live.stream.provider.rpc;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.qiyu.live.stream.interfaces.rpc.IResourceRpc;
import org.qiyu.live.stream.provider.service.IMinioStorageService;

/**
 * 通用资源上传 RPC 实现（直播间封面等图片直传 MinIO 公开读桶）
 */
@DubboService
public class ResourceRpcImpl implements IResourceRpc {

    @Resource
    private IMinioStorageService minioStorageService;

    @Override
    public String upload(byte[] data, String objectName, String contentType) {
        return minioStorageService.uploadBytes(data, objectName, contentType);
    }
}
