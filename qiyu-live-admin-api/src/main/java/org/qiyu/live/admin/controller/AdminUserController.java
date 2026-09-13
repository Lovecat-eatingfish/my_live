package org.qiyu.live.admin.controller;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.user.dto.UserBanDTO;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理：分页搜索、封禁/禁言、解封
 */
@RestController
@RequestMapping("/user")
public class AdminUserController {

    @DubboReference(check = false)
    private IUserRpc userRpc;

    @PostMapping("/list")
    public WebResponseVO list(String keyword, Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        List<UserDTO> list = userRpc.listUsers(keyword, p, ps);
        return WebResponseVO.success(list);
    }

    /**
     * 封禁/禁言：type=1 禁言，type=2 封号；minutes=0 或不传表示永久
     */
    @PostMapping("/ban")
    public WebResponseVO ban(Long userId, Integer type, Integer minutes, String reason) {
        if (userId == null || type == null) {
            return WebResponseVO.bizError("userId 和 type 必填");
        }
        UserBanDTO banDTO = new UserBanDTO();
        banDTO.setUserId(userId);
        banDTO.setType(type);
        banDTO.setDurationMinutes(minutes == null ? 0 : minutes);
        banDTO.setReason(reason);
        boolean ok = userRpc.banUser(banDTO);
        return ok ? WebResponseVO.success(true) : WebResponseVO.bizError("封禁失败");
    }

    @PostMapping("/unban")
    public WebResponseVO unban(Long userId, Integer type) {
        if (userId == null || type == null) {
            return WebResponseVO.bizError("userId 和 type 必填");
        }
        boolean ok = userRpc.unbanUser(userId, type);
        return ok ? WebResponseVO.success(true) : WebResponseVO.bizError("解封失败或记录不存在");
    }
}
