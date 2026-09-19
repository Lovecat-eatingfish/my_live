package org.qiyu.live.admin.auth;

import jakarta.annotation.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 运营台鉴权过滤器：除登录接口外全部校验 adminToken 头
 */
@Component
@Order(1)
public class AdminAuthFilter implements Filter {
    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }


    @Resource
    private AdminAuthService adminAuthService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String uri = req.getRequestURI();
        // 登录接口与静态资源放行；Swagger 文档放行
        if (uri.endsWith("/auth/login") || uri.endsWith("/auth/check")
                || uri.contains("/swagger-ui") || uri.contains("/api-docs")
                || uri.contains("/swagger-resources")) {
            chain.doFilter(request, response);
            return;
        }
        String token = req.getHeader("adminToken");
        if (!adminAuthService.checkToken(token)) {
            HttpServletResponse resp = (HttpServletResponse) response;
            resp.setStatus(401);
            resp.setContentType("application/json;charset=utf-8");
            resp.getWriter().write("{\"code\":401,\"msg\":\"登录已失效，请重新登录\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
