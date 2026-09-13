package org.qiyu.live.admin.controller;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.im.constants.AppIdEnum;
import org.qiyu.live.im.dto.ImMsgBody;
import org.qiyu.live.im.router.interfaces.rpc.ImRouterRpc;
import org.qiyu.live.user.dto.UserBanDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.qiyu.live.web.starter.error.BizBaseErrorEnum;
import org.qiyu.live.web.starter.error.ErrorAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 直播间巡查截帧：列表 / 处置（警告 5566 / 强制关播 / 封禁主播 / 标记正常）
 */
@RestController
@RequestMapping("/snapshot")
public class AdminSnapshotController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DubboReference(check = false)
    private IUserRpc userRpc;
    @DubboReference(check = false)
    private org.qiyu.live.living.interfaces.rpc.ILivingRoomRpc livingRoomRpc;
    @DubboReference(check = false)
    private ImRouterRpc routerRpc;

    /** 截帧列表（默认待审 0，按时间倒序） */
    @PostMapping("/list")
    public WebResponseVO list(Integer status, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        Integer st = status == null ? 0 : status;
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, room_id, anchor_id, img_url, audit_status, handle_action, create_time" +
                        " FROM risk_room_snapshot WHERE audit_status = ? ORDER BY id DESC LIMIT ? OFFSET ?",
                st, ps, (p - 1) * ps);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM risk_room_snapshot WHERE audit_status = ?", Long.class, st);
        return WebResponseVO.success(Map.of("list", list, "total", total == null ? 0 : total));
    }

    /**
     * 处置截帧记录：warn=发 5566 警告给主播；close=强制关播；ban=封禁主播 24h；pass=标记正常
     */
    @PostMapping("/handle")
    public WebResponseVO handle(Long id, String action) {
        ErrorAssert.isNotNull(id, BizBaseErrorEnum.PARAM_ERROR);
        ErrorAssert.isTure(action != null && List.of("warn", "close", "ban", "pass").contains(action),
                BizBaseErrorEnum.PARAM_ERROR);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT room_id, anchor_id FROM risk_room_snapshot WHERE id = ?", id);
        ErrorAssert.isTure(!rows.isEmpty(), BizBaseErrorEnum.PARAM_ERROR);
        long roomId = ((Number) rows.get(0).get("room_id")).longValue();
        long anchorId = ((Number) rows.get(0).get("anchor_id")).longValue();

        switch (action) {
            case "warn" -> sendWarn(anchorId, roomId);
            case "close" -> livingRoomRpc.closeLiving(
                    buildCloseReq((int) roomId, anchorId));
            case "ban" -> {
                UserBanDTO banDTO = new UserBanDTO();
                banDTO.setUserId(anchorId);
                banDTO.setType(UserBanDTO.TYPE_ACCOUNT_BAN);
                banDTO.setDurationMinutes(1440);
                banDTO.setReason("直播内容违规（巡查截帧处置）");
                userRpc.banUser(banDTO);
            }
            default -> { }
        }
        jdbcTemplate.update("UPDATE risk_room_snapshot SET audit_status = 2, handle_action = ? WHERE id = ?",
                "pass".equals(action) ? null : action, id);
        return WebResponseVO.success(true);
    }

    private org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO buildCloseReq(int roomId, Long anchorId) {
        org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO req = new org.qiyu.live.living.interfaces.dto.LivingRoomReqDTO();
        req.setRoomId(roomId);
        req.setAnchorId(anchorId);
        return req;
    }

    private void sendWarn(Long anchorId, long roomId) {
        JSONObject data = new JSONObject();
        data.put("userId", anchorId);
        data.put("roomId", roomId);
        data.put("type", 5566);
        data.put("content", "直播内容巡查提醒：请遵守社区规范，违规将被强制下播并封禁");
        ImMsgBody msgBody = new ImMsgBody();
        msgBody.setUserId(anchorId);
        msgBody.setAppId(AppIdEnum.QIYU_LIVE_BIZ.getCode());
        msgBody.setBizCode(5566);
        msgBody.setData(data.toJSONString());
        routerRpc.batchSendMsg(List.of(msgBody));
    }
}
