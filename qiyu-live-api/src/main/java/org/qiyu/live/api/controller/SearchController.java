package org.qiyu.live.api.controller;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.api.service.IVideoApiService;
import org.qiyu.live.common.interfaces.utils.ConvertBeanUtils;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局搜索（阶段一：MySQL LIKE 过渡，三分栏返回，ES 记入远期）
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;
    @DubboReference(check = false)
    private IUserRpc userRpc;
    @Resource
    private IVideoApiService videoApiService;

    /** 三分栏搜索：直播 / 视频 / 用户，各取前 10 */
    @PostMapping("/all")
    public WebResponseVO searchAll(String keyword) {
        ErrorAssert.isTure(StringUtils.hasText(keyword), BizBaseErrorEnum.PARAM_ERROR);
        String kw = keyword.trim();
        Map<String, Object> result = new HashMap<>();
        result.put("rooms", ConvertBeanUtils.convertList(
                livingRoomRpc.searchRooms(kw, 1, 10).getList(), org.qiyu.live.api.vo.resp.LivingRoomRespVO.class));
        result.put("videos", videoApiService.search(kw, 1, 10));
        result.put("users", userRpc.listUsers(kw, 1, 10));
        return WebResponseVO.success(result);
    }
}
