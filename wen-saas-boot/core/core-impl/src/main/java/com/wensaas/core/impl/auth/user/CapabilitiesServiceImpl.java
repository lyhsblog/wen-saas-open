package com.wensaas.core.impl.auth.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.user.CapabilitiesService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.menu.MenuQueryService;
import com.wensaas.core.api.menu.MenuQueryService.NavNode;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import com.wensaas.core.impl.auth.session.Session;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.permission.datascope.DataScope;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import com.wensaas.core.impl.permission.eval.UserPermissions;
import com.wensaas.core.impl.pkg.TenantPlansResolver;
import com.wensaas.core.impl.spi.module.SysBusinessModule;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link CapabilitiesService} 默认实现。
 *
 * <p>套餐数据模型升级后的规则：
 * <ul>
 *   <li>{@code enabledModules} = 租户 plans 内未撤销套餐的 moduleCodes ∩ 已注册业务模块
 *       （active + expired 都算可见；expired-only 模块标 readonly=true）</li>
 *   <li>{@code activePackages} = 租户 plans 中未撤销套餐（含过期 readonly=true）；详见 {@link TenantPlansResolver}</li>
 *   <li>{@code menus} = {@link MenuQueryService#navTree} 按当前端 scope + 用户权限解析后的可见菜单树
 *       （sys_menu 为唯一权威）；scope 按主体是否运营账号区分 OPS / TENANT</li>
 * </ul>
 *
 * <p>无独立缓存层：{@link PermissionResolver} 为请求级直查（仅整租户部门树跨请求缓存）；plans 解析查 3 张
 * 平台表 + jsonb 解析，量小可接受；v2 大流量再加 capabilities 整体缓存。
 */
public class CapabilitiesServiceImpl implements CapabilitiesService {

    private final PermissionResolver permissionResolver;
    private final SysBusinessModuleMapper moduleMapper;
    private final MenuQueryService menuQueryService;
    private final TenantPlansResolver tenantPlansResolver;
    private final SessionStore sessionStore;
    private final SysUserMapper userMapper;
    private final TenantQueryService tenantQuery;

    public CapabilitiesServiceImpl(PermissionResolver permissionResolver,
                                   SysBusinessModuleMapper moduleMapper,
                                   MenuQueryService menuQueryService,
                                   TenantPlansResolver tenantPlansResolver,
                                   SessionStore sessionStore,
                                   SysUserMapper userMapper,
                                   TenantQueryService tenantQuery) {
        this.permissionResolver = permissionResolver;
        this.moduleMapper = moduleMapper;
        this.menuQueryService = menuQueryService;
        this.tenantPlansResolver = tenantPlansResolver;
        this.sessionStore = sessionStore;
        this.userMapper = userMapper;
        this.tenantQuery = tenantQuery;
    }

    @Override
    public Capabilities resolve() {
        UserPrincipal principal = UserContext.require();
        UserPermissions perm = permissionResolver.resolve(principal.tenantId(), principal.userId());

        TenantPlansResolver.ResolvedPlans plans = tenantPlansResolver.resolve(principal.tenantId());

        List<ModuleView> enabledModules = loadEnabledModules(
                plans.activeModuleCodes(), plans.readonlyModuleCodes());

        int scope = principal.tenantId() == TenantContext.PLATFORM_TENANT_ID
                ? MenuQueryService.SCOPE_OPS
                : MenuQueryService.SCOPE_TENANT;
        // 套餐门控：未开通的受控业务模块菜单不下发（连主管理员也不可见），与 API 层 402 一致
        Set<String> enabledModuleCodes = enabledModules.stream()
                .map(ModuleView::code)
                .collect(java.util.stream.Collectors.toSet());
        List<MenuNode> menus = mapNav(
                menuQueryService.navTree(scope, perm.permissions(), principal.tenantOwner(), enabledModuleCodes));

        List<String> permissions = perm.permissions().stream().sorted().toList();
        List<String> visibleDeptIds = perm.scope() == DataScope.DEPT_AND_BELOW
                ? perm.deptIds().stream().map(String::valueOf).sorted().toList()
                : List.of();

        // 代登录态：从当前 session 读取（operatorName 已于代登录 session 创建时快照，不依赖运营原 session 存活）
        Session session = sessionStore.findBySessionId(principal.sessionId()).orElse(null);
        boolean impersonating = session != null && session.originalSessionId() != null;
        String operatorName = impersonating ? session.operatorName() : null;

        // 防截屏水印：真实姓名（未加密，供水印溯源）。登录态已快照 realName，直接读内存；
        // 仅合成主体 / 历史会话未带时回退查库（+ 租户级开关 NULL=默认开启；运营域无 sys_tenant 行同样默认开启）
        String realName = principal.realName();
        if (realName == null || realName.isBlank()) {
            SysUser self = userMapper.selectById(principal.userId());
            realName = self != null ? self.getRealName() : null;
        }
        TenantInfo tenant = tenantQuery.tenantById(principal.tenantId());
        boolean screenshotWatermark = tenant == null
                || tenant.screenshotWatermarkEnabled() == null
                || tenant.screenshotWatermarkEnabled();

        return new Capabilities(
                String.valueOf(principal.userId()),
                String.valueOf(principal.tenantId()),
                principal.tenantOwner(),
                permissions,
                perm.scope().name(),
                visibleDeptIds,
                enabledModules,
                plans.packageViews(),
                menus,
                impersonating,
                operatorName,
                realName,
                screenshotWatermark);
    }

    /**
     * 按租户激活/过期模块集合 ∩ 已注册业务模块（{@code sys_business_module}）取交集。
     * 若两个集合都为空（租户未持任何套餐 / 平台无业务模块），直接返回空列表（避免查全表）。
     */
    private List<ModuleView> loadEnabledModules(Set<String> activeCodes, Set<String> readonlyCodes) {
        Set<String> visibleCodes = new HashSet<>(activeCodes);
        visibleCodes.addAll(readonlyCodes);
        if (visibleCodes.isEmpty()) {
            return List.of();
        }
        List<SysBusinessModule> rows = moduleMapper.selectList(
                new LambdaQueryWrapper<SysBusinessModule>()
                        .in(SysBusinessModule::getCode, visibleCodes)
                        .orderByAsc(SysBusinessModule::getCode));
        List<ModuleView> views = new ArrayList<>(rows.size());
        for (SysBusinessModule row : rows) {
            boolean readonly = readonlyCodes.contains(row.getCode()) && !activeCodes.contains(row.getCode());
            views.add(new ModuleView(row.getCode(), row.getName(), row.getVersion(), readonly));
        }
        return views;
    }

    /** 把 {@link MenuQueryService} 的导航节点映射为能力包对外的 {@link MenuNode}（GROUP 无 path）。 */
    private static List<MenuNode> mapNav(List<NavNode> nav) {
        List<MenuNode> out = new ArrayList<>(nav.size());
        for (NavNode n : nav) {
            out.add(new MenuNode(n.path(), n.label(), n.icon(), n.sort(), mapNav(n.children())));
        }
        return out;
    }
}
