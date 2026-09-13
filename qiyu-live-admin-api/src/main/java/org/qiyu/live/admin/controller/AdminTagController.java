package org.qiyu.live.admin.controller;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.video.dto.VideoDTO;
import org.qiyu.live.video.interfaces.rpc.IVideoRpc;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 标签管理：视频标签 CRUD
 */
@RestController
@RequestMapping("/tag")
public class AdminTagController {

    @DubboReference(check = false)
    private IVideoRpc videoRpc;

    @PostMapping("/list")
    public WebResponseVO list() {
        return WebResponseVO.success(videoRpc.listTags());
    }

    @PostMapping("/add")
    public WebResponseVO add(String name) {
        ErrorAssert.isTure(name != null && !name.trim().isEmpty(), BizBaseErrorEnum.PARAM_ERROR);
        Integer id = videoRpc.addTag(name.trim());
        return WebResponseVO.success(Map.of("id", id == null ? 0 : id));
    }

    @PostMapping("/rename")
    public WebResponseVO rename(Integer id, String name) {
        ErrorAssert.isNotNull(id, BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isTure(name != null && !name.trim().isEmpty(), BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(videoRpc.renameTag(id, name.trim()));
    }

    @PostMapping("/delete")
    public WebResponseVO delete(Integer id) {
        ErrorAssert.isNotNull(id, BizBaseErrorEnum.PARAM_ERROR);
        return WebResponseVO.success(videoRpc.deleteTag(id));
    }
}
