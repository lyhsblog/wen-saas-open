package com.wensaas.core.impl.auth.mfa;

import com.wensaas.core.api.auth.user.AuthenticatedIdentity;

/**
 * 登录密码校验通过、二步验证未完成时的预认证态（存 Redis，短 TTL）。
 *
 * <p>携带完整 {@link AuthenticatedIdentity} 字段，以便二步验证通过后无需再查库即可创建会话。
 * setup* 字段仅在强制模式首次配置（{@link #enabled()}=false）的 setup→verify 过程中填充。
 */
public record MfaPreAuth(
        long userId,
        long tenantId,
        String username,
        boolean tenantOwner,
        Long mainDeptId,
        String realName,
        String account,
        String loginMethod,
        boolean enabled,
        String setupMethod,
        String tentativeSecret,
        String setupTarget) {

    public static MfaPreAuth ofLogin(AuthenticatedIdentity id, String account, String loginMethod,
                                     boolean enabled) {
        return new MfaPreAuth(id.userId(), id.tenantId(), id.username(), id.tenantOwner(),
                id.mainDeptId(), id.realName(), account, loginMethod, enabled, null, null, null);
    }

    public MfaPreAuth withSetup(String setupMethod, String tentativeSecret, String setupTarget) {
        return new MfaPreAuth(userId, tenantId, username, tenantOwner, mainDeptId, realName, account,
                loginMethod, enabled, setupMethod, tentativeSecret, setupTarget);
    }

    public AuthenticatedIdentity toIdentity() {
        return new AuthenticatedIdentity(userId, tenantId, username, tenantOwner, mainDeptId,
                false, null, realName);
    }
}
