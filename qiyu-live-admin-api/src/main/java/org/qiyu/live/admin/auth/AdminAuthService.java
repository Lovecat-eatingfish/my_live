package org.qiyu.live.admin.auth;

import jakarta.annotation.Resource;
import org.qiyu.live.admin.dao.mapper.AdminUserMapper;
import org.qiyu.live.admin.dao.po.AdminUserPO;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理员登录态：内存 token（内部系统，单实例部署足够）
 */
@Service
public class AdminAuthService {

    /** token -> 过期时间戳 */
    private final Map<String, Long> tokenCache = new ConcurrentHashMap<>();
    private static final long TTL_MS = 12 * 60 * 60 * 1000L;

    @Resource
    private AdminUserMapper adminUserMapper;

    /**
     * 登录校验，成功返回 token，失败返回 null
     */
    public String login(String username, String password) {
        AdminUserPO po = adminUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminUserPO>()
                        .eq(AdminUserPO::getUsername, username)
                        .last("limit 1"));
        if (po == null || po.getStatus() == null || po.getStatus() != 1) {
            return null;
        }
        if (!hash(password, po.getSalt()).equals(po.getPasswordHash())) {
            return null;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenCache.put(token, System.currentTimeMillis() + TTL_MS);
        return token;
    }

    public boolean checkToken(String token) {
        if (token == null) {
            return false;
        }
        Long expireAt = tokenCache.get(token);
        if (expireAt == null) {
            return false;
        }
        if (expireAt < System.currentTimeMillis()) {
            tokenCache.remove(token);
            return false;
        }
        return true;
    }

    public void logout(String token) {
        if (token != null) {
            tokenCache.remove(token);
        }
    }

    private String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((password + salt).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
