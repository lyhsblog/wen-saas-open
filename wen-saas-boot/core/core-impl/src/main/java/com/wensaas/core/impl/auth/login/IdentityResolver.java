package com.wensaas.core.impl.auth.login;

import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.api.auth.org.DeptQueryService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import com.wensaas.core.impl.auth.user.SysUser;

import java.util.Objects;

/**
 * 已认证用户 → {@link AuthenticatedIdentity} 的公共组装：填充 tenantOwner / mainDeptId。
 * 账号密码登录与 OAuth 登录共用，避免逻辑重复。
 *
 * <p>调用方（{@link PasswordLoginIdentityProvider} / {@code OAuthLoginIdentityProvider} 的 authenticate、
 * {@code AuthServiceImpl.login}）已带 {@code @CrossTenantQuery}，本类内的 mapper 查询在其线程级 ignore
 * 范围内执行，MP TenantLine 全程跳过，故本类自身无需再标注。
 */
public class IdentityResolver {

    private final TenantQueryService tenantQuery;
    private final DeptQueryService deptQuery;

    public IdentityResolver(TenantQueryService tenantQuery, DeptQueryService deptQuery) {
        this.tenantQuery = tenantQuery;
        this.deptQuery = deptQuery;
    }

    /** 组装已认证身份（byApiKey 恒 false）。 */
    public AuthenticatedIdentity of(SysUser user) {
        return new AuthenticatedIdentity(user.getId(), user.getTenantId(), user.getUsername(),
                resolveTenantOwner(user), resolveMainDeptId(user), false, null, user.getRealName());
    }

    /** 运营账号始终 false；租户成员查 sys_tenant.main_admin_user_id 判定。 */
    public boolean resolveTenantOwner(SysUser user) {
        if (user.getTenantId() == null || user.getTenantId() == TenantContext.PLATFORM_TENANT_ID) {
            return false;
        }
        TenantInfo tenant = tenantQuery.tenantById(user.getTenantId());
        return tenant != null && Objects.equals(tenant.mainAdminUserId(), user.getId());
    }

    /** 查 sys_user_dept.is_main=1；未配置主部门返回 null。 */
    public Long resolveMainDeptId(SysUser user) {
        return deptQuery.mainDeptIdOfUser(user.getId());
    }
}
