package com.wensaas.core.api.ops.access;

import java.util.Set;

/**
 * 运营请求的访问上下文：包含 userId / dataScope / permissions。
 *
 * <p>由 {@code OpsAccessContextResolver} 在请求维度内解析（{@code RequestContext} 缓存），
 * Service 层显式调用 {@link #hasPermission(String)} 做权限点判定，
 * 调用 {@link #dataScope()} 做数据范围过滤。
 *
 * @param userId 当前运营用户 ID（{@code sys_user.id}，{@code tenant_id=0}）
 * @param dataScope 多角色合并后的数据范围（取最宽松）
 * @param permissions 所有 {@code ops.*} 权限点集合（多角色并集）
 */
public record OpsAccessContext(long userId, OpsDataScope dataScope, Set<String> permissions) {

    public OpsAccessContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean hasPermission(String code) {
        return permissions.contains(code);
    }

    public boolean isFullScope() {
        return dataScope == OpsDataScope.OPS_ALL;
    }
}
