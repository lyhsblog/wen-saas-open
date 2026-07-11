package com.wensaas.core.impl.auth.login;

import com.wensaas.core.api.auth.login.AuthenticationException;
import com.wensaas.core.impl.auth.password.SecurityPolicySettings;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败计数 + 临时锁定守卫（Redis 滑动窗口实现）。
 *
 * <p>Redis key: {@code login-fail:{tenantId}:{username}}，TTL = windowSeconds。
 * <p>原子操作用 Lua 脚本保证 INCR + EXPIRE 在同一事务内，避免竞态。
 */
public class LoginAttemptGuard {

    /** Lua: INCR key; EXPIRE key ttl (only if new key); return current count. */
    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>(
            """
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return c
            """,
            Long.class);

    private final StringRedisTemplate redis;
    private final SecurityPolicySettings security;

    public LoginAttemptGuard(StringRedisTemplate redis, SecurityPolicySettings security) {
        this.redis = redis;
        this.security = security;
    }

    /**
     * 登录前调用：如果账号已锁定，直接抛 LOGIN_LOCKED。
     */
    public void check(long tenantId, String username) {
        String key = key(tenantId, username);
        String val = redis.opsForValue().get(key);
        if (val != null && Integer.parseInt(val) >= security.loginMaxAttempts()) {
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            String msg = ttl != null && ttl > 0
                    ? "账号已锁定，请 " + ttl + " 秒后重试"
                    : "账号已锁定，请稍后重试";
            throw new AuthenticationException(AuthenticationException.Code.LOGIN_LOCKED, msg);
        }
    }

    /**
     * 密码错误时调用：计数 +1，返回剩余可尝试次数（第 3 次及以后非 null）。
     *
     * @return 剩余次数（从第 3 次失败起返回，之前返回 null）
     */
    public Integer recordFailure(long tenantId, String username) {
        String key = key(tenantId, username);
        Long count = redis.execute(INCR_SCRIPT, List.of(key), String.valueOf(security.loginWindowSeconds()));
        if (count == null) return null;
        int remaining = security.loginMaxAttempts() - count.intValue();
        // 第 3 次（count>=3）开始提示剩余次数
        if (count >= 3 && remaining > 0) {
            return remaining;
        }
        return null;
    }

    /** 登录成功后清除计数。 */
    public void clearFailure(long tenantId, String username) {
        redis.delete(key(tenantId, username));
    }

    /** 运营端手动解锁。 */
    public void unlock(long tenantId, String username) {
        redis.delete(key(tenantId, username));
    }

    /**
     * 查询账号当前锁定状态（成员管理详情展示用，只读不改计数）。
     *
     * @return 是否锁定 + 剩余锁定秒数（未锁定为 0）
     */
    public LockStatus lockStatus(long tenantId, String username) {
        String key = key(tenantId, username);
        String val = redis.opsForValue().get(key);
        if (val == null || Integer.parseInt(val) < security.loginMaxAttempts()) {
            return new LockStatus(false, 0L);
        }
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return new LockStatus(true, ttl != null && ttl > 0 ? ttl : 0L);
    }

    /** 账号锁定状态快照。 */
    public record LockStatus(boolean locked, long remainingSeconds) {
    }

    private String key(long tenantId, String username) {
        return "login-fail:" + tenantId + ":" + username;
    }
}
