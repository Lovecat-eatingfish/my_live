package org.qiyu.live.api.service.impl;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.api.service.IResourceService;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通用资源上传服务实现（图片经 stream-provider 的 MinIO RPC 落桶）
 */
@Service
public class ResourceServiceImpl implements IResourceService {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /** 常见图片 MIME -> 扩展名，浏览器上传时以 Content-Type 为准 */
    private static final Map<String, String> MIME_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    @DubboReference(check = false)
    private org.qiyu.live.stream.interfaces.rpc.IResourceRpc resourceRpc;

    @Override
    public String uploadImage(MultipartFile file, Long userId) {
        String ext = resolveExt(file);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String objectName = "covers/" + datePath + "/" + userId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "." + ext;
        try {
            return resourceRpc.upload(file.getBytes(), objectName, file.getContentType());
        } catch (Exception e) {
            throw new RuntimeException("上传封面失败", e);
        }
    }

    private String resolveExt(MultipartFile file) {
        String ext = null;
        String mime = file.getContentType();
        if (StringUtils.hasText(mime) && MIME_EXT.containsKey(mime.toLowerCase())) {
            ext = MIME_EXT.get(mime.toLowerCase());
        } else {
            String name = file.getOriginalFilename();
            if (StringUtils.hasText(name) && name.contains(".")) {
                ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            }
        }
        ErrorAssert.isTure(ext != null && ALLOWED_EXT.contains(ext), BizBaseErrorEnum.PARAM_ERROR);
        return ext;
    }
}
