package com.wensaas.core.impl.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 认证与会话配置：JWT 秘钥 / issuer、access 与 refresh TTL、白名单路径、密码重置 token TTL 等。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.auth")
@Getter
@Setter
public class AuthProperties {

    /**
     * JWT HS256 秘钥（至少 256bit = 32 字节）。必配，留默认仅 dev 便利，prod 必须由环境变量注入。
     */
    private String jwtSecret = "dev-only-secret-please-change-in-prod-32bytes!";

    /**
     * JWT issuer，与 claim {@code iss} 比对。
     */
    private String issuer = "wen-saas-boot";

    /**
     * 会话 / refresh token 的默认 TTL（未勾"记住设备"）。也是 Redis Session key 的存活时长，
     * 每次 {@code /api/auth/refresh} 滑动顺延。可被运营在线配置 {@code session_ttl_seconds} 覆盖。默认 2h。
     */
    private Duration tokenTtl = Duration.ofHours(2);

    /**
     * Access token（JWT）TTL。短时效，过期由前端凭 refresh token（httpOnly cookie 自动携带）静默续期。默认 15min。
     */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /**
     * 勾选"记住当前设备"时 refresh token / 会话的 TTL。默认 30d。
     */
    private Duration rememberRefreshTtl = Duration.ofDays(30);

    /**
     * refresh cookie 是否带 Secure 属性。prod 必 true（HTTPS）；dev（http://localhost）置 false 便于联调。
     */
    private boolean refreshCookieSecure = true;

    /**
     * 白名单路径（Ant 通配）。AuthenticationFilter 命中后不强制认证。
     */
    private List<String> permitAll = new ArrayList<>();

    /**
     * 密码重置 Token TTL（默认 48h）。
     */
    private Duration resetTokenTtl = Duration.ofHours(48);

    /**
     * 是否在 UserContext 为空时降级走 {@code X-Tenant-Id} header 解析（兼容 dev 与内部探针）。
     * <p>prod 默认 false。
     */
    private boolean allowTenantIdHeader = false;

    /**
     * 运营账号（tenant_id=0）是否强制登录二步验证。默认 false：与租户账号一致，
     * 由用户在个人中心自助开关（未开启则密码直登）。采用方对运营侧有强合规要求时
     * 设 true：未开启的运营账号登录时将被强制完成二步验证配置后才发会话。
     */
    private boolean opsMfaEnforced = false;

    /** 登录失败锁定策略。 */
    private LoginAttempt loginAttempt = new LoginAttempt();

    @Getter
    @Setter
    public static class LoginAttempt {
        /** 锁定阈值：连续失败达此次数后账号进入锁定状态。 */
        private int maxAttempts = 5;
        /** 滑动窗口 / 锁定时长（秒）。 */
        private int windowSeconds = 600;

    }

}
