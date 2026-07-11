package com.wensaas.core.api.auth.user;

import java.util.Objects;

/**
 * 已认证身份（Provider 输出，AuthService 据此创建 Session 并升级为 {@link UserPrincipal}）。
 *
 * <p>与 {@link UserPrincipal} 的区别：此类型尚不携带 sessionId。{@code tenantOwner} 由 Provider
 * 在认证时根据 {@code sys_tenant.main_admin_user_id} 计算填充。{@code mainDeptId} 由 Provider
 * 在认证时查询 {@code sys_user_dept.is_main=1} 填充，可能为 null。
 *
 * <p>{@code byApiKey} / {@code apiKeyId}：用户名密码登录走 Provider 时恒为 {@code false / null}；
 * 开放 API 凭证场景不走 Provider，直接在 {@code AppKeyAuthenticationFilter} 构造 {@link UserPrincipal}。
 */
public record AuthenticatedIdentity(long userId, long tenantId, String username,
                                     boolean tenantOwner, Long mainDeptId,
                                     boolean byApiKey, Long apiKeyId, String realName) {

    public AuthenticatedIdentity {
        Objects.requireNonNull(username, "username");
        if (byApiKey && apiKeyId == null) {
            throw new IllegalArgumentException("byApiKey=true 时 apiKeyId 必须非空");
        }
    }

    /** 兼容旧 7 参构造：realName 置 null。 */
    public AuthenticatedIdentity(long userId, long tenantId, String username,
                                 boolean tenantOwner, Long mainDeptId, boolean byApiKey, Long apiKeyId) {
        this(userId, tenantId, username, tenantOwner, mainDeptId, byApiKey, apiKeyId, null);
    }
}
