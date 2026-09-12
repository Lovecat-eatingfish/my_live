package org.qiyu.live.stream.provider.service;

/**
 * MinIO 对象存储服务（直播录制文件）
 */
public interface IMinioStorageService {

    /**
     * 确保桶存在且可公开读（回放文件浏览器直链播放）
     * 应用启动时调用
     */
    void ensureBucketReady();

    /**
     * 上传本地录制文件到 MinIO
     *
     * @param localFilePath 本地文件路径
     * @param objectName    对象名（桶内路径）
     * @return 浏览器可访问的公开 URL
     */
    String uploadRecordFile(String localFilePath, String objectName);

    /**
     * 上传内存中的二进制资源（如直播间封面图片）到 MinIO
     *
     * @param data        文件二进制内容
     * @param objectName  对象名（桶内路径）
     * @param contentType MIME 类型
     * @return 浏览器可访问的公开 URL
     */
    String uploadBytes(byte[] data, String objectName, String contentType);
}
