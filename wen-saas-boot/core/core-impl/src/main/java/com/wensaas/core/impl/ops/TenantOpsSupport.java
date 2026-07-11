package com.wensaas.core.impl.ops;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.TenantOpsException;
import com.wensaas.core.api.ops.access.OpsAccessContext;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.ops.access.OpsDataScope;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;

/**
 * 运营租户管理各协作类的跨桶共享件：租户加载校验 + 数据范围守卫 + 展示名工具。
 *
 * <p>与协作类一致标 {@code @CrossTenantQuery}（{@link com.wensaas.core.impl.tenant.isolation.IgnoreStrategyScopes}
 * 栈式作用域嵌套安全，重复进入无副作用）。原 {@code TenantOpsServiceImpl} 拆分时抽出，
 * 承载真正跨桶复用的逻辑（M18）。
 *
 * <p>数据范围守卫（{@link #requireScope}）：跨租户聚合读（租户列表等）按 dataScope 在查询里过滤，
 * 路径带单个 tenantId 的<b>读写端点</b>统一走本守卫——{@code OPS_OWNED}（客户经理）只允许访问
 * {@code sys_tenant.csm_user_id == 自己} 的租户，否则抛 {@code FORBIDDEN}（403）；
 * {@code OPS_ALL}（超管）直接放行。与工单域 {@code OpsTicketServiceImpl.requireScope} 同范式。
 * 例外：租户详情读保持 404 语义（不泄露存在性，见 {@code TenantDetailOpsService.getTenantDetail}）。
 */
@CrossTenantQuery("TenantOpsSupport: 运营操作公共件，无租户上下文")
public class TenantOpsSupport {

    private final SysTenantMapper tenantMapper;
    private final OpsAccessContextResolver accessResolver;

    public TenantOpsSupport(SysTenantMapper tenantMapper, OpsAccessContextResolver accessResolver) {
        this.tenantMapper = tenantMapper;
        this.accessResolver = accessResolver;
    }

    /** 加载租户，不存在抛 {@code TENANT_NOT_FOUND}。 */
    public SysTenant requireTenant(long tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new TenantOpsException(TenantOpsException.Code.TENANT_NOT_FOUND,
                    "租户不存在：id=" + tenantId);
        }
        return tenant;
    }

    /** 加载租户 + 数据范围守卫：单租户读写路径的统一入口。 */
    public SysTenant requireTenantInScope(long tenantId) {
        SysTenant tenant = requireTenant(tenantId);
        requireScope(tenant);
        return tenant;
    }

    /** 数据范围守卫（已持有租户实体时用，免二次查库）。 */
    public void requireScope(SysTenant tenant) {
        if (isTenantSelfAccess(tenant.getId())) {
            return;
        }
        OpsAccessContext ctx = accessResolver.require();
        if (ctx.dataScope() != OpsDataScope.OPS_OWNED) {
            return;
        }
        if (tenant.getCsmUserId() == null || tenant.getCsmUserId() != ctx.userId()) {
            throw new TenantOpsException(TenantOpsException.Code.FORBIDDEN,
                    "无权操作该租户（非名下客户）：id=" + tenant.getId());
        }
    }

    /** 数据范围守卫（仅有 tenantId 时用；OPS_ALL 直接放行、不查库）。 */
    public void requireScope(long tenantId) {
        if (isTenantSelfAccess(tenantId)) {
            return;
        }
        OpsAccessContext ctx = accessResolver.require();
        if (ctx.dataScope() != OpsDataScope.OPS_OWNED) {
            return;
        }
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getCsmUserId() == null || tenant.getCsmUserId() != ctx.userId()) {
            throw new TenantOpsException(TenantOpsException.Code.FORBIDDEN,
                    "无权操作该租户（非名下客户）：id=" + tenantId);
        }
    }

    /**
     * 租户端复用分支：TenantOpsService 的单租户读视图也被租户自助端复用（如自助「变更历史」
     * 走 {@code listTenantEvents}）。此时登录主体是租户账号（tenant_id != 0），无运营访问上下文
     * （{@code OpsAccessContextResolver.require()} 会抛异常）——只允许访问自己租户（兜底校验，
     * 正常由租户隔离层保证传入自身 tenantId），跳过运营数据范围守卫。
     * 运营请求（tenant_id=0，经 OpsAuthWebFilter）返回 false，继续走 OPS_OWNED 校验。
     */
    private boolean isTenantSelfAccess(long targetTenantId) {
        UserPrincipal p = UserContext.require();
        if (p.tenantId() == TenantContext.PLATFORM_TENANT_ID) {
            return false;
        }
        if (p.tenantId() != targetTenantId) {
            throw new TenantOpsException(TenantOpsException.Code.FORBIDDEN,
                    "无权访问其他租户数据：id=" + targetTenantId);
        }
        return true;
    }

    /** 展示名 = realName（NOT NULL）。 */
    public static String csmDisplayName(SysUser u) {
        return u.getRealName();
    }
}
