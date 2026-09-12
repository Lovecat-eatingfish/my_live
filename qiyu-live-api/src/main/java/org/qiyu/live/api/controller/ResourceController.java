package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.qiyu.live.api.service.IResourceService;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.web.bind.annotation.PostMapping;
import org.qiyu.live.web.starter.context.QiyuRequestContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用资源上传（直播间封面等图片）
 */
@RestController
@RequestMapping("/resource")
public class ResourceController {

    /** 封面图片大小上限 5MB */
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L;

    @Resource
    private IResourceService resourceService;

    @PostMapping("/upload")
    public WebResponseVO upload(@RequestParam("file") MultipartFile file) {
        ErrorAssert.isTure(file != null && !file.isEmpty(), BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isTure(file.getSize() <= MAX_IMAGE_SIZE, BizBaseErrorEnum.PARAM_ERROR);
        String url = resourceService.uploadImage(file, QiyuRequestContext.getUserId());
        return WebResponseVO.success(url);
    }
}
