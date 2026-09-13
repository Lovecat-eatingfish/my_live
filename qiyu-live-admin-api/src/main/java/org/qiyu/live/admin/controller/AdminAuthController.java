package org.qiyu.live.admin.controller;

import jakarta.annotation.Resource;
import org.qiyu.live.admin.auth.AdminAuthService;
import org.qiyu.live.common.interfaces.vo.WebResponseVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AdminAuthController {

    @Resource
    private AdminAuthService adminAuthService;

    @PostMapping("/login")
    public WebResponseVO login(String username, String password) {
        String token = adminAuthService.login(username, password);
        if (token == null) {
            return WebResponseVO.bizError("账号或密码错误");
        }
        return WebResponseVO.success(Map.of("token", token));
    }

    @PostMapping("/logout")
    public WebResponseVO logout(@RequestHeader(value = "adminToken", required = false) String token) {
        adminAuthService.logout(token);
        return WebResponseVO.success();
    }
}
