package org.qiyu.live.admin.controller;

import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboReference;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.qiyu.live.user.dto.UserDTO;
import org.qiyu.live.user.interfaces.IUserRpc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理：分页搜索
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
}
