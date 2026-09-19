package org.qiyu.live.admin.auth;

import jakarta.annotation.Resource;
import org.qiyu.live.admin.dao.mapper.AdminUserMapper;
import org.qiyu.live.admin.dao.po.AdminUserPO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 管理员登录态：token 存 Redis（12h TTL），重启不丢、多实例部署就绪
 */
@Service
public class AdminAuthService {

    private static final String TOKEN_KEY_PREFIX = "qiyu-live-admin-api:admin:token:";
    private static final long TTL_SECONDS = 12 * 60 * 60L;

    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
        stringRedisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, "1", TTL_SECONDS, TimeUnit.SECONDS);
        return token;
    }

    public boolean checkToken(String token) {
        if (token == null) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(TOKEN_KEY_PREFIX + token));
    }

    public void logout(String token) {
        if (token != null) {
            stringRedisTemplate.delete(TOKEN_KEY_PREFIX + token);
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
