package org.qiyu.live.api.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 通用资源上传服务
 */
public interface IResourceService {

    /**
     * 上传图片到 MinIO 公开读桶
     *
     * @param file   图片文件
     * @param userId 上传用户（用于对象路径隔离）
     * @return 浏览器可直接访问的公开 URL
     */
    String uploadImage(MultipartFile file, Long userId);
}
