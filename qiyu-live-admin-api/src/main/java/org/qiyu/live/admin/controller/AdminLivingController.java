package org.qiyu.live.admin.controller;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.living.interfaces.dto.LivingRoomRespDTO;
import org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 直播管理：当前直播列表 / 强制关播
 */
@RestController
@RequestMapping("/living")
public class AdminLivingController {

    @DubboReference(check = false)
    private ILivingRoomRpc livingRoomRpc;

    @PostMapping("/list")
    public WebResponseVO list(Integer type) {
        // type=0 查全部类型
        List<Map<String, Object>> result = new ArrayList<>();
        for (int t = 1; t <= 4; t++) {
            if (type != null && type > 0 && type != t) {
                continue;
            }
            try {
                org.qiyu.live.common.interfaces.dto.PageWrapper<LivingRoomRespDTO> wrapper =
                        livingRoomRpc.list(reqOf(t));
                for (LivingRoomRespDTO dto : wrapper.getList()) {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("roomId", dto.getId());
                    item.put("anchorId", dto.getAnchorId());
                    item.put("roomName", dto.getRoomName());
                    item.put("type", dto.getType());
                    item.put("watchNum", dto.getWatchNum());
                    result.add(item);
                }
            } catch (Exception ignored) {
                // 单类型失败不影响其他类型
            }
        }
        return WebResponseVO.success(result);
    }

    private org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO reqOf(int type) {
        org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO req = new org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO();
        req.setType(type);
        req.setPage(1);
        req.setPageSize(100);
        return req;
    }

    /** 强制关播（运营处置） */
    @PostMapping("/forceClose")
    public WebResponseVO forceClose(Integer roomId) {
        ErrorAssert.isNotNull(roomId, BizBaseErrorEnum.PARAM_ERROR);
        LivingRoomRespDTO room = livingRoomRpc.queryByRoomId(roomId);
        ErrorAssert.isNotNull(room, BizBaseErrorEnum.PARAM_ERROR);
        org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO req = new org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO();
        req.setRoomId(roomId);
        // 以主播身份关播（closeLiving 只允许主播本人）
        req.setAnchorId(room.getAnchorId());
        return WebResponseVO.success(livingRoomRpc.closeLiving(req));
    }
}
