package org.qiyu.live.stream.interfaces.rpc;

/**
 * 通用资源上传 RPC（直播间封面等图片直传 MinIO）
 */
public interface IResourceRpc {

    /**
     * 上传二进制资源到 MinIO 公开读桶
     *
     * @param data        文件二进制内容
     * @param objectName  桶内对象路径（如 covers/20260912/xxx.jpg）
     * @param contentType MIME 类型（如 image/jpeg）
     * @return 浏览器可直接访问的公开 URL
     */
    String upload(byte[] data, String objectName, String contentType);
}
