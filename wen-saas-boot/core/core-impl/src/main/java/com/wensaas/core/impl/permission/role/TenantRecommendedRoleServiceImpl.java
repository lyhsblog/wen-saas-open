package com.wensaas.core.impl.permission.role;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.permission.PermissionCatalogService;
import com.wensaas.core.api.auth.permission.TenantRecommendedRoleException;
import com.wensaas.core.api.auth.permission.TenantRecommendedRoleService;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplate;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplateActivation;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplateActivationMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplateMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePackage;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePackageMapper;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePermission;
import com.wensaas.core.impl.ops.role.SysRecommendedRoleTemplatePermissionMapper;
import com.wensaas.core.impl.pkg.TenantPlansResolver;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.wensaas.core.impl.permission.datascope.DataScope;
import com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore;
import com.wensaas.core.impl.permission.eval.SysRolePermission;
import com.wensaas.core.impl.permission.eval.SysRolePermissionMapper;

/**
 * 租户侧推荐角色实现：读取运营发布的推荐角色模板，一键激活为本租户角色（含权限码快照）。
 *
 * <p>推荐角色模板系列表为纯平台表（白名单，不触发租户隔离注入）；本服务在租户上下文中读取，
 * 写 {@code sys_role}/{@code sys_role_permission}（自动注入 tenant_id）与
 * {@code sys_recommended_role_template_activation}（tenant_id 仅作数据列，显式设值）。
 */
public class TenantRecommendedRoleServiceImpl implements TenantRecommendedRoleService {

    private static final int STATUS_PUBLISHED = 1;

    private final SysRecommendedRoleTemplateMapper templateMapper;
    private final SysRecommendedRoleTemplatePackageMapper packageMapper;
    private final SysRecommendedRoleTemplatePermissionMapper permissionMapper;
    private final SysRecommendedRoleTemplateActivationMapper activationMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final RoleDefaultScopeStore defaultScopeStore;
    private final TenantPlansResolver tenantPlansResolver;
    private final PermissionCatalogService permissionCatalogService;

    public TenantRecommendedRoleServiceImpl(SysRecommendedRoleTemplateMapper templateMapper,
                                            SysRecommendedRoleTemplatePackageMapper packageMapper,
                                            SysRecommendedRoleTemplatePermissionMapper permissionMapper,
                                            SysRecommendedRoleTemplateActivationMapper activationMapper,
                                            SysRoleMapper roleMapper,
                                            SysRolePermissionMapper rolePermissionMapper,
                                            RoleDefaultScopeStore defaultScopeStore,
                                            TenantPlansResolver tenantPlansResolver,
                                            PermissionCatalogService permissionCatalogService) {
        this.templateMapper = templateMapper;
        this.packageMapper = packageMapper;
        this.permissionMapper = permissionMapper;
        this.activationMapper = activationMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.defaultScopeStore = defaultScopeStore;
        this.tenantPlansResolver = tenantPlansResolver;
        this.permissionCatalogService = permissionCatalogService;
    }

    @Override
    public List<RecommendedRoleItem> listAvailable() {
        long tenantId = TenantContext.require();
        List<SysRecommendedRoleTemplate> published = templateMapper.selectList(
                new LambdaQueryWrapper<SysRecommendedRoleTemplate>()
                        .eq(SysRecommendedRoleTemplate::getStatus, STATUS_PUBLISHED));
        if (published.isEmpty()) return List.of();

        Set<String> tenantPkgs = tenantPackageCodes(tenantId);
        List<Long> tplIds = published.stream().map(SysRecommendedRoleTemplate::getId).toList();
        Map<Long, List<String>> pkgsByTpl = packagesByTemplate(tplIds);
        Map<Long, Integer> permCountByTpl = permCountByTemplate(tplIds);
        Map<Long, SysRecommendedRoleTemplateActivation> activationByTpl = liveActivations(tenantId, tplIds);

        List<RecommendedRoleItem> out = new ArrayList<>();
        for (SysRecommendedRoleTemplate t : published) {
            List<String> pkgs = pkgsByTpl.getOrDefault(t.getId(), List.of());
            // 必须关联至少一个本租户已勾选套餐包
            if (pkgs.stream().noneMatch(tenantPkgs::contains)) continue;
            SysRecommendedRoleTemplateActivation act = activationByTpl.get(t.getId());
            out.add(new RecommendedRoleItem(
                    t.getId(), t.getName(), t.getDescription(),
                    t.getDataScope() == null ? 3 : t.getDataScope(),
                    pkgs, permCountByTpl.getOrDefault(t.getId(), 0),
                    act != null, act == null ? null : act.getCreatedRoleId()));
        }
        return out;
    }

    @Override
    public ActivatePreview preview(long templateId) {
        long tenantId = TenantContext.require();
        SysRecommendedRoleTemplate t = requirePublishedMatching(tenantId, templateId);
        List<String> codes = templatePermissionCodes(templateId);
        Map<String, String> nameByCode = catalogNameMap();
        Set<String> valid = nameByCode.keySet();
        List<PermissionLine> lines = codes.stream()
                .map(c -> new PermissionLine(c, nameByCode.getOrDefault(c, c), valid.contains(c)))
                .toList();
        return new ActivatePreview(t.getId(), t.getName(),
                t.getDataScope() == null ? 3 : t.getDataScope(), lines);
    }

    @Override
    @Transactional
    public long activate(long templateId, ActivateCommand cmd) {
        long tenantId = TenantContext.require();
        SysRecommendedRoleTemplate t = requirePublishedMatching(tenantId, templateId);

        String name = cmd != null && cmd.name() != null && !cmd.name().isBlank()
                ? cmd.name().trim() : t.getName();
        if (roleMapper.countByName(tenantId, name, null) > 0) {
            throw new TenantRecommendedRoleException(TenantRecommendedRoleException.Code.ROLE_NAME_DUPLICATE,
                    "角色名已存在：" + name);
        }
        int dataScope = cmd != null && cmd.dataScope() != null ? cmd.dataScope()
                : (t.getDataScope() == null ? 3 : t.getDataScope());
        List<String> codes = cmd != null && cmd.permissionCodes() != null
                ? cmd.permissionCodes() : templatePermissionCodes(templateId);

        SysRole role = new SysRole();
        role.setTenantId(tenantId);
        role.setCode(uniqueRoleCode(tenantId, templateId));
        role.setName(name);
        role.setDescription(t.getDescription());
        role.setStatus(1);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        role.setCreatedBy(currentUserId());
        role.setUpdatedBy(currentUserId());
        roleMapper.insert(role);
        // 角色默认范围落 sys_role_data_scope NULL-action 行（sys_role.data_scope 已退役）
        defaultScopeStore.put(tenantId, role.getId(), DataScope.fromCode(dataScope));

        Set<String> seen = new LinkedHashSet<>();
        for (String c : codes) {
            if (c == null || c.isBlank() || !seen.add(c.trim())) continue;
            SysRolePermission rp = new SysRolePermission();
            rp.setTenantId(tenantId);
            rp.setRoleId(role.getId());
            rp.setPermissionCode(c.trim());
            rp.setCreatedAt(LocalDateTime.now());
            rp.setCreatedBy(currentUserId());
            rolePermissionMapper.insert(rp);
        }

        // 重置该模板在本租户的启用登记（旧角色若已删除则记录陈旧，覆盖之）
        activationMapper.delete(new LambdaQueryWrapper<SysRecommendedRoleTemplateActivation>()
                .eq(SysRecommendedRoleTemplateActivation::getTemplateId, templateId)
                .eq(SysRecommendedRoleTemplateActivation::getTenantId, tenantId));
        SysRecommendedRoleTemplateActivation act = new SysRecommendedRoleTemplateActivation();
        act.setTemplateId(templateId);
        act.setTenantId(tenantId);
        act.setCreatedRoleId(role.getId());
        act.setActivatedAt(LocalDateTime.now());
        act.setActivatedBy(currentUserId());
        activationMapper.insert(act);
        return role.getId();
    }

    // ===================== helpers =====================

    private SysRecommendedRoleTemplate requirePublishedMatching(long tenantId, long templateId) {
        SysRecommendedRoleTemplate t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new TenantRecommendedRoleException(TenantRecommendedRoleException.Code.TEMPLATE_NOT_FOUND,
                    "推荐角色模板不存在：" + templateId);
        }
        if (t.getStatus() == null || t.getStatus() != STATUS_PUBLISHED) {
            throw new TenantRecommendedRoleException(TenantRecommendedRoleException.Code.TEMPLATE_NOT_PUBLISHED,
                    "推荐角色模板未发布");
        }
        List<String> pkgs = packagesByTemplate(List.of(templateId)).getOrDefault(templateId, List.of());
        Set<String> tenantPkgs = tenantPackageCodes(tenantId);
        if (pkgs.stream().noneMatch(tenantPkgs::contains)) {
            throw new TenantRecommendedRoleException(TenantRecommendedRoleException.Code.PACKAGE_NOT_MATCHED,
                    "该推荐角色未关联本租户已勾选的套餐包");
        }
        return t;
    }

    private Set<String> tenantPackageCodes(long tenantId) {
        return tenantPlansResolver.resolve(tenantId).packageViews().stream()
                .map(com.wensaas.core.api.auth.user.CapabilitiesService.PackageView::code)
                .collect(Collectors.toSet());
    }

    private Map<Long, List<String>> packagesByTemplate(List<Long> tplIds) {
        if (tplIds.isEmpty()) return Map.of();
        Map<Long, List<String>> out = new HashMap<>();
        for (SysRecommendedRoleTemplatePackage p : packageMapper.selectList(
                new LambdaQueryWrapper<SysRecommendedRoleTemplatePackage>()
                        .in(SysRecommendedRoleTemplatePackage::getTemplateId, tplIds))) {
            out.computeIfAbsent(p.getTemplateId(), k -> new ArrayList<>()).add(p.getPackageCode());
        }
        return out;
    }

    private Map<Long, Integer> permCountByTemplate(List<Long> tplIds) {
        if (tplIds.isEmpty()) return Map.of();
        Map<Long, Integer> out = new HashMap<>();
        for (SysRecommendedRoleTemplatePermission p : permissionMapper.selectList(
                new LambdaQueryWrapper<SysRecommendedRoleTemplatePermission>()
                        .in(SysRecommendedRoleTemplatePermission::getTemplateId, tplIds))) {
            out.merge(p.getTemplateId(), 1, Integer::sum);
        }
        return out;
    }

    private List<String> templatePermissionCodes(long templateId) {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysRecommendedRoleTemplatePermission>()
                        .eq(SysRecommendedRoleTemplatePermission::getTemplateId, templateId))
                .stream().map(SysRecommendedRoleTemplatePermission::getPermissionCode).toList();
    }

    /** 当前租户对各模板的"有效"启用登记（关联角色未被软删）。 */
    private Map<Long, SysRecommendedRoleTemplateActivation> liveActivations(long tenantId, List<Long> tplIds) {
        if (tplIds.isEmpty()) return Map.of();
        List<SysRecommendedRoleTemplateActivation> rows = activationMapper.selectList(
                new LambdaQueryWrapper<SysRecommendedRoleTemplateActivation>()
                        .eq(SysRecommendedRoleTemplateActivation::getTenantId, tenantId)
                        .in(SysRecommendedRoleTemplateActivation::getTemplateId, tplIds));
        Map<Long, SysRecommendedRoleTemplateActivation> out = new HashMap<>();
        for (SysRecommendedRoleTemplateActivation a : rows) {
            if (a.getCreatedRoleId() == null) continue;
            SysRole role = roleMapper.selectById(a.getCreatedRoleId());
            if (role != null) {
                out.put(a.getTemplateId(), a);
            }
        }
        return out;
    }

    private Map<String, String> catalogNameMap() {
        Map<String, String> out = new HashMap<>();
        for (PermissionCatalogService.CatalogModule m : permissionCatalogService.listCatalog()) {
            for (PermissionCatalogService.CatalogMenu menu : m.menus()) {
                for (PermissionCatalogService.CatalogPoint p : menu.points()) {
                    out.put(p.code(), p.name());
                }
            }
        }
        return out;
    }

    private String uniqueRoleCode(long tenantId, long templateId) {
        for (int i = 0; i < 8; i++) {
            String code = "rec_" + templateId + "_" + Long.toString(System.nanoTime() & 0xFFFFFFL, 36);
            if (roleMapper.countByCode(tenantId, code, null) == 0) {
                return code;
            }
        }
        return "rec_" + templateId + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private static Long currentUserId() {
        UserPrincipal p = UserContext.get();
        return p == null ? null : p.userId();
    }
}
