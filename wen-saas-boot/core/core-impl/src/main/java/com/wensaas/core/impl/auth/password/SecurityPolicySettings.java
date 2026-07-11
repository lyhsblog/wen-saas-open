package com.wensaas.core.impl.auth.password;

import com.wensaas.core.api.ops.setting.OpsSettingService;
import com.wensaas.core.api.ops.setting.SettingCategory;
import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import com.wensaas.core.impl.auth.session.RedisSessionStore;
import com.wensaas.core.impl.auth.login.LoginAttemptGuard;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 安全策略参数读取门面。
 *
 * <p>运营可在「系统参数 / 安全策略」Tab 运行时调整登录锁定阈值、密码长度下限、会话时长；
 * 这些底座组件（{@link LoginAttemptGuard} / {@link JwtTokenProvider} / {@link RedisSessionStore} /
 * 密码强度校验调用方）统一经此门面读取：
 *
 * <ul>
 *   <li>优先读 {@code sys_setting(category=security_policy)}（{@link OpsSettingService} 经统一 CacheRegion
 *       缓存：进程内 L1 + Redis L2 + pub/sub 跨节点失效）</li>
 *   <li>{@link OpsSettingService} 不在当前上下文（如纯 auth 单元/集成测试）时，回退 {@link AuthProperties} 默认值</li>
 * </ul>
 *
 * <p>{@code OpsSettingService} 用 {@link ObjectProvider} 惰性可选注入，避免 auth 装配反向强依赖 ops 装配。
 */
public class SecurityPolicySettings {

    /** sys_setting 安全策略键名（与种子保持一致）。 */
    static final String KEY_LOGIN_MAX_ATTEMPTS = "login_max_attempts";
    static final String KEY_LOGIN_WINDOW_SECONDS = "login_window_seconds";
    static final String KEY_PASSWORD_MIN_LENGTH = "password_min_length";
    static final String KEY_SESSION_TTL_SECONDS = "session_ttl_seconds";

    /** 密码长度下限默认值（与 PasswordStrengthValidator 历史常量一致）。 */
    static final int DEFAULT_PASSWORD_MIN_LENGTH = 8;

    private final ObjectProvider<OpsSettingService> settingProvider;
    private final AuthProperties props;

    public SecurityPolicySettings(ObjectProvider<OpsSettingService> settingProvider, AuthProperties props) {
        this.settingProvider = settingProvider;
        this.props = props;
    }

    /** 登录失败锁定阈值。缺省回退 {@code auth.login-attempt.max-attempts}。 */
    public int loginMaxAttempts() {
        return getInt(KEY_LOGIN_MAX_ATTEMPTS, props.getLoginAttempt().getMaxAttempts());
    }

    /** 登录失败滑动窗口 / 锁定时长（秒）。缺省回退 {@code auth.login-attempt.window-seconds}。 */
    public int loginWindowSeconds() {
        return getInt(KEY_LOGIN_WINDOW_SECONDS, props.getLoginAttempt().getWindowSeconds());
    }

    /** 密码长度下限。缺省 8。 */
    public int passwordMinLength() {
        return getInt(KEY_PASSWORD_MIN_LENGTH, DEFAULT_PASSWORD_MIN_LENGTH);
    }

    /** 会话 / 访问令牌 TTL（秒）。缺省回退 {@code auth.token-ttl}。 */
    public long sessionTtlSeconds() {
        return getInt(KEY_SESSION_TTL_SECONDS, (int) props.getTokenTtl().toSeconds());
    }

    private int getInt(String key, int dflt) {
        OpsSettingService s = settingProvider.getIfAvailable();
        if (s == null) {
            return dflt;
        }
        Integer v = s.get(SettingCategory.SECURITY_POLICY, key, Integer.class, dflt);
        return v != null ? v : dflt;
    }
}
