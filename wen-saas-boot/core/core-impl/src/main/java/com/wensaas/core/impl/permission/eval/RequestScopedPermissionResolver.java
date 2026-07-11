package com.wensaas.core.impl.permission.eval;

import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.impl.permission.datascope.DataScope;
import com.wensaas.core.impl.permission.datascope.SysRoleDataScope;
import com.wensaas.core.impl.permission.org.DeptService;
import com.wensaas.core.impl.tenant.isolation.IgnoreStrategyScopes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 请求级 {@link PermissionResolver}。每个判定经一条索引覆盖的连表直查 DB，结果在请求内复用，
 * 跨请求不缓存——彻底去掉旧的「用户/角色/范围多层缓存 + 全量聚合」，失效依赖面随之消失；唯独整租户部门树仍由
 * {@link DeptService} 跨请求缓存（失效触发点单一）。
 *
 * <p>数据范围采用 grant-coupled 语义：某 action 的范围只在「授予该 action 权限点」的角色内取最宽，
 * 每角色 {@code action 覆盖 ?? 角色默认 ?? SELF}。
 *
 * <p>请求边界用 {@link RequestContext} 的 requestId 守卫 ThreadLocal memo：requestId 变化即丢弃旧 memo；
 * requestId 为空（无请求上下文）则不 memo、每次直查。所有 DB 查询显式带 tenant_id 并绕过 MP 租户行注入
 * （复用「运营加载某租户权限」的跨租户路径）。
 */
public class RequestScopedPermissionResolver implements PermissionResolver {

    private static final ThreadLocal<Holder> HOLDER = new ThreadLocal<>();

    private final PermissionEvalMapper mapper;
    private final DeptService deptService;

    public RequestScopedPermissionResolver(PermissionEvalMapper mapper, DeptService deptService) {
        this.mapper = mapper;
        this.deptService = deptService;
    }

    @Override
    public boolean has(long tenantId, long userId, String code) {
        return hasAny(tenantId, userId, List.of(code));
    }

    @Override
    public boolean hasAny(long tenantId, long userId, Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return false;
        }
        Holder h = holder();
        if (h == null) {
            return query(() -> mapper.existsAnyGrant(tenantId, userId, codes));
        }
        String key = tenantId + ":" + userId + ":" + codes.stream().sorted().collect(Collectors.joining(","));
        return h.grantCache.computeIfAbsent(key, k -> query(() -> mapper.existsAnyGrant(tenantId, userId, codes)));
    }

    @Override
    public EffectiveScope scopeFor(long tenantId, long userId, String action) {
        Holder h = holder();
        if (h == null) {
            return loadScope(tenantId, userId, action);
        }
        return h.scopeCache.computeIfAbsent(tenantId + ":" + userId + ":" + action,
                k -> loadScope(tenantId, userId, action));
    }

    @Override
    public UserPermissions resolve(long tenantId, long userId) {
        Holder h = holder();
        if (h == null) {
            return loadFull(tenantId, userId);
        }
        return h.fullCache.computeIfAbsent(tenantId + ":" + userId, k -> loadFull(tenantId, userId));
    }

    /** grant-coupled：在授予该 action 的角色内按「覆盖 ?? 默认 ?? SELF」取最宽，再物化部门集合。 */
    private EffectiveScope loadScope(long tenantId, long userId, String action) {
        List<SysRoleDataScope> rows = query(() -> mapper.listGrantedRoleScopes(tenantId, userId, action));
        if (rows.isEmpty()) {
            // 未授予该权限点（正常不会走到——切面已先拦）；保守取最严
            return new EffectiveScope(DataScope.SELF, Set.of());
        }
        Map<Long, DataScope> overrideByRole = new HashMap<>();
        Map<Long, DataScope> defaultByRole = new HashMap<>();
        Set<Long> grantedRoles = new HashSet<>();
        for (SysRoleDataScope row : rows) {
            grantedRoles.add(row.getRoleId());
            if (row.getDataScope() == null) {
                continue; // LEFT JOIN 落空：授予但无范围配置 → 该角色落到 SELF
            }
            DataScope ds = DataScope.fromCode(row.getDataScope());
            if (row.getAction() == null) {
                defaultByRole.merge(row.getRoleId(), ds, DataScope::widest);
            } else {
                overrideByRole.merge(row.getRoleId(), ds, DataScope::widest);
            }
        }
        DataScope widest = DataScope.SELF;
        for (Long roleId : grantedRoles) {
            DataScope effective = overrideByRole.getOrDefault(roleId,
                    defaultByRole.getOrDefault(roleId, DataScope.SELF));
            widest = DataScope.widest(widest, effective);
        }
        return new EffectiveScope(widest, materializeDepts(tenantId, userId, widest));
    }

    private UserPermissions loadFull(long tenantId, long userId) {
        Set<String> permissions = Set.copyOf(query(() -> mapper.listAllPermissionCodes(tenantId, userId)));
        DataScope scope = DataScope.SELF;
        for (Integer code : query(() -> mapper.listRoleDefaultScopeCodes(tenantId, userId))) {
            scope = DataScope.widest(scope, DataScope.fromCode(code));
        }
        Set<Long> deptIds = scope == DataScope.DEPT_AND_BELOW
                ? materializeDepts(tenantId, userId, scope)
                : Set.of();
        return new UserPermissions(permissions, scope, deptIds);
    }

    /** DEPT_ONLY → 直属部门；DEPT_AND_BELOW → 直属 + 子孙展开；其余 → 空集。 */
    private Set<Long> materializeDepts(long tenantId, long userId, DataScope scope) {
        return switch (scope) {
            case DEPT_ONLY -> Set.copyOf(query(() -> mapper.listOwnDeptIds(tenantId, userId)));
            case DEPT_AND_BELOW -> {
                List<Long> own = query(() -> mapper.listOwnDeptIds(tenantId, userId));
                yield own.isEmpty() ? Set.of() : deptService.expandSelfAndDescendants(tenantId, own);
            }
            case ALL, SELF -> Set.of();
        };
    }

    /** 显式带 tenant_id 的权限查询统一绕过 MP 租户行注入（运营跨租户加载 / 登录早期 TenantContext 未就绪都需要）。 */
    private <T> T query(Supplier<T> action) {
        try (var ignored = IgnoreStrategyScopes.enter(IgnoreStrategyScopes.TENANT_LINE)) {
            return action.get();
        }
    }

    private static Holder holder() {
        String requestId = RequestContext.get();
        if (requestId == null) {
            return null;
        }
        Holder h = HOLDER.get();
        if (h == null || !requestId.equals(h.requestId)) {
            h = new Holder(requestId);
            HOLDER.set(h);
        }
        return h;
    }

    /** 请求内 memo：按权限点集合 / action / (tenant:user) 分别缓存，避免同请求重复直查。 */
    private static final class Holder {
        final String requestId;
        final Map<String, Boolean> grantCache = new HashMap<>();
        final Map<String, EffectiveScope> scopeCache = new HashMap<>();
        final Map<String, UserPermissions> fullCache = new HashMap<>();

        Holder(String requestId) {
            this.requestId = requestId;
        }
    }
}
