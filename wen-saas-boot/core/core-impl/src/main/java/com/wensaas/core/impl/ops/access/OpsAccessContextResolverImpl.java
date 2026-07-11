package com.wensaas.core.impl.ops.access;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.access.OpsAccessContext;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.permission.datascope.DataScope;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import com.wensaas.core.impl.permission.eval.UserPermissions;

/**
 * 复用 {@link PermissionResolver}（请求级直查）解析运营访问上下文。
 *
 * <p>映射：租户域 {@link DataScope} → 运营域 {@link OpsDataScope}：
 * {@code ALL → OPS_ALL}；{@code SELF → OPS_OWNED}；
 * {@code DEPT_AND_BELOW → OPS_OWNED}（保守降级，运营域不应出现）。
 */
public class OpsAccessContextResolverImpl implements OpsAccessContextResolver {

    private final PermissionResolver permissionResolver;

    public OpsAccessContextResolverImpl(PermissionResolver permissionResolver) {
        this.permissionResolver = permissionResolver;
    }

    @Override
    public OpsAccessContext require() {
        UserPrincipal p = UserContext.get();
        if (p == null) {
            throw new IllegalStateException("OpsAccessContext.require: UserContext 未注入（应已被 OpsAuthWebFilter 保护）");
        }
        if (p.tenantId() != TenantContext.PLATFORM_TENANT_ID) {
            throw new IllegalStateException("OpsAccessContext.require: 当前用户非运营账号，tenant_id=" + p.tenantId());
        }
        return resolve(p.userId());
    }

    @Override
    public OpsAccessContext resolve(long userId) {
        UserPermissions up = permissionResolver.resolve(TenantContext.PLATFORM_TENANT_ID, userId);
        OpsDataScope scope = switch (up.scope()) {
            case ALL -> OpsDataScope.OPS_ALL;
            case SELF, DEPT_AND_BELOW, DEPT_ONLY -> OpsDataScope.OPS_OWNED;
        };
        return new OpsAccessContext(userId, scope, up.permissions());
    }
}
