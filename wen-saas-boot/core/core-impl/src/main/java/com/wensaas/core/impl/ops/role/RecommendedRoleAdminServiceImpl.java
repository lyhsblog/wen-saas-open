package com.wensaas.core.impl.ops.role;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.api.ops.role.RecommendedRoleAdminException;
import com.wensaas.core.api.ops.role.RecommendedRoleAdminService;
import com.wensaas.core.api.pkg.PackageAdminService;
import com.wensaas.core.api.spi.def.PermissionDef;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.permission.role.SysRoleMapper;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.pkg.SysPackage;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link RecommendedRoleAdminService} 默认实现：推荐角色模板的草稿 / 发布 / 下架全生命周期维护。
 *
 * <p>所涉 4 张表均为纯平台表（platform-only-tables 白名单），租户拦截器跳过，无需处理 tenant 上下文。
 */
public class RecommendedRoleAdminServiceImpl implements RecommendedRoleAdminService {

    static final int STATUS_DRAFT = 0;
    static final int STATUS_PUBLISHED = 1;
    static final int STATUS_INACTIVE = 2;

    private static final String OPS_PERM_PREFIX = "ops.";
    private static final int PKG_ARCHIVED = PackageAdminService.PackageStatus.ARCHIVED.code();
    /** 列表默认排序优先级：已发布 > 草稿 > 已下架。 */
    private static final Map<Integer, Integer> STATUS_SORT_RANK = Map.of(
            STATUS_PUBLISHED, 0, STATUS_DRAFT, 1, STATUS_INACTIVE, 2);

    private final SysRecommendedRoleTemplateMapper templateMapper;
    private final SysRecommendedRoleTemplatePackageMapper packageMapper;
    private final SysRecommendedRoleTemplatePermissionMapper permissionMapper;
    private final SysRecommendedRoleTemplateActivationMapper activationMapper;
    private final SysPackageMapper sysPackageMapper;
    private final PermissionRegistry permissionRegistry;
    private final TenantOpsService tenantOpsService;
    private final SysTenantMapper sysTenantMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final com.wensaas.core.api.menu.MenuQueryService menuQueryService;
    private final com.wensaas.core.impl.pkg.PackageModuleGate packageGate;

    public RecommendedRoleAdminServiceImpl(SysRecommendedRoleTemplateMapper templateMapper,
                                           SysRecommendedRoleTemplatePackageMapper packageMapper,
                                           SysRecommendedRoleTemplatePermissionMapper permissionMapper,
                                           SysRecommendedRoleTemplateActivationMapper activationMapper,
                                           SysPackageMapper sysPackageMapper,
                                           PermissionRegistry permissionRegistry,
                                           TenantOpsService tenantOpsService,
                                           SysTenantMapper sysTenantMapper,
                                           SysUserMapper sysUserMapper,
                                           SysRoleMapper sysRoleMapper,
                                           SysUserRoleMapper sysUserRoleMapper,
                                           com.wensaas.core.api.menu.MenuQueryService menuQueryService,
                                           com.wensaas.core.impl.pkg.PackageModuleGate packageGate) {
        this.templateMapper = templateMapper;
        this.packageMapper = packageMapper;
        this.permissionMapper = permissionMapper;
        this.activationMapper = activationMapper;
        this.sysPackageMapper = sysPackageMapper;
        this.permissionRegistry = permissionRegistry;
        this.tenantOpsService = tenantOpsService;
        this.sysTenantMapper = sysTenantMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.menuQueryService = menuQueryService;
        this.packageGate = packageGate;
    }

    @Override
    public List<TemplateListItem> listTemplates(StatusFilter status, String keyword) {
        var q = Wrappers.<SysRecommendedRoleTemplate>lambdaQuery();
        if (status != null && status != StatusFilter.ALL) {
            q.eq(SysRecommendedRoleTemplate::getStatus, statusFilterToInt(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            q.like(SysRecommendedRoleTemplate::getName, "%" + keyword.trim() + "%");
        }
        List<SysRecommendedRoleTemplate> rows = templateMapper.selectList(q);
        if (rows.isEmpty()) return List.of();

        List<Long> ids = rows.stream().map(SysRecommendedRoleTemplate::getId).toList();
        Map<Long, List<String>> packagesByTpl = packageMapper.selectList(
                Wrappers.<SysRecommendedRoleTemplatePackage>lambdaQuery()
                        .in(SysRecommendedRoleTemplatePackage::getTemplateId, ids))
                .stream().collect(Collectors.groupingBy(SysRecommendedRoleTemplatePackage::getTemplateId,
                        Collectors.mapping(SysRecommendedRoleTemplatePackage::getPackageCode,
                                Collectors.toList())));
        Map<Long, Long> permCountByTpl = permissionMapper.selectList(
                Wrappers.<SysRecommendedRoleTemplatePermission>lambdaQuery()
                        .in(SysRecommendedRoleTemplatePermission::getTemplateId, ids))
                .stream().collect(Collectors.groupingBy(SysRecommendedRoleTemplatePermission::getTemplateId,
                        Collectors.counting()));
        Map<Long, Long> actCountByTpl = activationMapper.selectList(
                Wrappers.<SysRecommendedRoleTemplateActivation>lambdaQuery()
                        .in(SysRecommendedRoleTemplateActivation::getTemplateId, ids))
                .stream().collect(Collectors.groupingBy(SysRecommendedRoleTemplateActivation::getTemplateId,
                        Collectors.counting()));

        return rows.stream()
                .sorted(Comparator
                        .comparingInt((SysRecommendedRoleTemplate t) ->
                                STATUS_SORT_RANK.getOrDefault(t.getStatus(), Integer.MAX_VALUE))
                        .thenComparing(SysRecommendedRoleTemplate::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> {
                    List<String> pkgs = new ArrayList<>(packagesByTpl.getOrDefault(t.getId(), List.of()));
                    Collections.sort(pkgs);
                    return new TemplateListItem(
                            t.getId(), t.getName(), t.getDescription(),
                            TemplateStatus.fromCode(t.getStatus()), t.getDataScope(),
                            pkgs,
                            permCountByTpl.getOrDefault(t.getId(), 0L).intValue(),
                            actCountByTpl.getOrDefault(t.getId(), 0L).intValue(),
                            toInstant(t.getCreatedAt()), toInstant(t.getUpdatedAt()));
                })
                .toList();
    }

    @Override
    @Transactional
    public TemplateDetail getTemplate(long id) {
        SysRecommendedRoleTemplate t = requireTemplate(id);
        List<String> pkgCodes = loadPackageCodes(id);
        List<String> permCodes = loadPermissionCodes(id);
        List<AssociatedPackage> packages = buildPackages(pkgCodes);
        boolean allArchived = isAllArchived(packages);
        // OPS-R-023 惰性翻转：已发布且关联套餐包全部归档 → 自动下架（保护新租户不再看到无效推荐）
        if (t.getStatus() == STATUS_PUBLISHED && allArchived) {
            t.setStatus(STATUS_INACTIVE);
            t.setUpdatedBy(currentUserId());
            templateMapper.updateById(t);
        }
        return new TemplateDetail(
                t.getId(), t.getName(), t.getDescription(),
                TemplateStatus.fromCode(t.getStatus()), t.getDataScope(),
                pkgCodes, permCodes, packages, invalidPermissions(permCodes), allArchived,
                countActivations(id),
                toInstant(t.getCreatedAt()), toInstant(t.getUpdatedAt()));
    }

    @Override
    @CrossTenantQuery("RecommendedRoleAdmin: 已启用租户列表跨租户读 sys_tenant/sys_user/sys_role/sys_user_role")
    public ActivationPage listActivations(long id, int page, int pageSize) {
        requireTemplate(id);
        int p = page < 1 ? 1 : page;
        int size = pageSize < 1 ? 20 : pageSize;
        Page<SysRecommendedRoleTemplateActivation> pg = activationMapper.selectPage(
                new Page<>(p, size),
                Wrappers.<SysRecommendedRoleTemplateActivation>lambdaQuery()
                        .eq(SysRecommendedRoleTemplateActivation::getTemplateId, id)
                        .orderByDesc(SysRecommendedRoleTemplateActivation::getActivatedAt));
        List<SysRecommendedRoleTemplateActivation> rows = pg.getRecords();
        if (rows.isEmpty()) {
            return new ActivationPage(List.of(), pg.getTotal(), p, size);
        }
        List<Long> tenantIds = rows.stream()
                .map(SysRecommendedRoleTemplateActivation::getTenantId).distinct().toList();
        Map<Long, SysTenant> tenantById = sysTenantMapper.selectBatchIds(tenantIds).stream()
                .collect(Collectors.toMap(SysTenant::getId, x -> x, (a, b) -> a));
        Set<Long> csmIds = tenantById.values().stream()
                .map(SysTenant::getCsmUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> userById = csmIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(csmIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, x -> x, (a, b) -> a));
        // 批量取快照角色名（已被租户删除的角色不在结果里 → 陈旧记录，roleName 留空）
        List<Long> roleIds = rows.stream()
                .map(SysRecommendedRoleTemplateActivation::getCreatedRoleId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, SysRole> roleById = roleIds.isEmpty() ? Map.of()
                : sysRoleMapper.selectBatchIds(roleIds).stream()
                        .collect(Collectors.toMap(SysRole::getId, x -> x, (a, b) -> a));
        List<ActivationItem> items = rows.stream().map(a -> {
            SysTenant tenant = tenantById.get(a.getTenantId());
            SysUser csm = tenant != null && tenant.getCsmUserId() != null
                    ? userById.get(tenant.getCsmUserId()) : null;
            SysRole role = a.getCreatedRoleId() != null ? roleById.get(a.getCreatedRoleId()) : null;
            // 成员数走自定义 XML（显式 tenantId，不依赖租户拦截器）；角色已删则记 0
            int memberCount = role != null
                    ? sysUserRoleMapper.countByRole(a.getTenantId(), a.getCreatedRoleId()) : 0;
            return new ActivationItem(
                    a.getTenantId(),
                    tenant != null ? tenant.getName() : null,
                    tenant != null ? tenant.getCode() : null,
                    csm != null ? csmDisplayName(csm) : null,
                    a.getCreatedRoleId(),
                    role != null ? role.getName() : null,
                    memberCount,
                    toInstant(a.getActivatedAt()));
        }).toList();
        return new ActivationPage(items, pg.getTotal(), p, size);
    }

    @Override
    @Transactional
    public TemplateDetail create(CreateCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("模板名不能为空");
        }
        validateDataScope(cmd.dataScope());
        Set<String> pkgs = dedupRequirePackages(cmd.packageCodes());
        Set<String> perms = dedupValidatePermissions(cmd.permissionCodes());
        if (templateMapper.selectCount(Wrappers.<SysRecommendedRoleTemplate>lambdaQuery()
                .eq(SysRecommendedRoleTemplate::getName, cmd.name())) > 0) {
            throw new RecommendedRoleAdminException(
                    RecommendedRoleAdminException.Code.NAME_CONFLICT, "模板名已存在: " + cmd.name());
        }

        Long operatorId = currentUserId();
        SysRecommendedRoleTemplate t = new SysRecommendedRoleTemplate();
        t.setName(cmd.name());
        t.setDescription(cmd.description());
        t.setDataScope(cmd.dataScope());
        t.setStatus(cmd.publishImmediately() ? STATUS_PUBLISHED : STATUS_DRAFT);
        t.setCreatedBy(operatorId);
        t.setUpdatedBy(operatorId);
        templateMapper.insert(t);

        insertPackages(t.getId(), pkgs);
        insertPermissions(t.getId(), perms);

        List<String> sortedPkgs = new ArrayList<>(pkgs);
        Collections.sort(sortedPkgs);
        List<String> sortedPerms = new ArrayList<>(perms);
        Collections.sort(sortedPerms);
        return toDetail(t, sortedPkgs, sortedPerms, 0);
    }

    @Override
    @Transactional
    public TemplateDetail update(long id, UpdateCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        SysRecommendedRoleTemplate t = requireTemplate(id);
        // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(id), t.getName(), cmd.name());

        boolean dirty = false;
        if (cmd.name() != null && !cmd.name().isBlank() && !cmd.name().equals(t.getName())) {
            if (templateMapper.selectCount(Wrappers.<SysRecommendedRoleTemplate>lambdaQuery()
                    .eq(SysRecommendedRoleTemplate::getName, cmd.name())
                    .ne(SysRecommendedRoleTemplate::getId, id)) > 0) {
                throw new RecommendedRoleAdminException(
                        RecommendedRoleAdminException.Code.NAME_CONFLICT, "模板名已存在: " + cmd.name());
            }
            t.setName(cmd.name());
            dirty = true;
        }
        if (cmd.description() != null) {
            t.setDescription(cmd.description());
            dirty = true;
        }
        if (cmd.dataScope() != null) {
            validateDataScope(cmd.dataScope());
            t.setDataScope(cmd.dataScope());
            dirty = true;
        }
        if (dirty) {
            t.setUpdatedBy(currentUserId());
            templateMapper.updateById(t);
        }

        List<String> finalPkgs;
        if (cmd.packageCodes() != null) {
            finalPkgs = reconcilePackages(t, cmd.packageCodes());
        } else {
            finalPkgs = loadPackageCodes(id);
        }
        List<String> finalPerms;
        if (cmd.permissionCodes() != null) {
            finalPerms = reconcilePermissions(t, cmd.permissionCodes());
        } else {
            finalPerms = loadPermissionCodes(id);
        }
        return toDetail(t, finalPkgs, finalPerms, countActivations(id));
    }

    @Override
    @Transactional
    public void publish(long id) {
        SysRecommendedRoleTemplate t = requireTemplate(id);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(id), t.getName());
        if (t.getStatus() == STATUS_PUBLISHED) return;
        if (countPackages(id) == 0) {
            throw new RecommendedRoleAdminException(
                    RecommendedRoleAdminException.Code.NO_PACKAGE_ASSOCIATED, "发布前需关联至少 1 个套餐包");
        }
        // OPS-R-023：关联套餐包全部已归档则无法发布（与读时惰性下架同源规则）
        if (isAllArchived(buildPackages(loadPackageCodes(id)))) {
            throw new RecommendedRoleAdminException(
                    RecommendedRoleAdminException.Code.INVALID_STATUS_TRANSITION, "关联套餐包均已归档，无法发布");
        }
        t.setStatus(STATUS_PUBLISHED);
        t.setUpdatedBy(currentUserId());
        templateMapper.updateById(t);
    }

    @Override
    @Transactional
    public void unpublish(long id) {
        SysRecommendedRoleTemplate t = requireTemplate(id);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(id), t.getName());
        if (t.getStatus() != STATUS_PUBLISHED) {
            throw new RecommendedRoleAdminException(
                    RecommendedRoleAdminException.Code.INVALID_STATUS_TRANSITION, "仅已发布状态可下架");
        }
        t.setStatus(STATUS_INACTIVE);
        t.setUpdatedBy(currentUserId());
        templateMapper.updateById(t);
    }

    @Override
    @Transactional
    public void delete(long id) {
        SysRecommendedRoleTemplate t = requireTemplate(id);
        // ② 审计对象名：加载后即登记当前名（业务前，失败也留意图）
        AuditObjects.add(null, String.valueOf(id), t.getName());
        if (countActivations(id) > 0) {
            throw new RecommendedRoleAdminException(
                    RecommendedRoleAdminException.Code.TEMPLATE_IN_USE, "模板已被租户启用过，仅可下架不可删除");
        }
        packageMapper.delete(Wrappers.<SysRecommendedRoleTemplatePackage>lambdaQuery()
                .eq(SysRecommendedRoleTemplatePackage::getTemplateId, id));
        permissionMapper.delete(Wrappers.<SysRecommendedRoleTemplatePermission>lambdaQuery()
                .eq(SysRecommendedRoleTemplatePermission::getTemplateId, id));
        templateMapper.deleteById(id);
    }

    @Override
    @Transactional
    public TemplateDetail copy(long sourceId, CopyCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        SysRecommendedRoleTemplate source = requireTemplate(sourceId);
        // 复用创建路径：恒为草稿（publishImmediately=false）
        return create(new CreateCommand(
                cmd.name(), source.getDescription(), source.getDataScope(),
                loadPackageCodes(sourceId), loadPermissionCodes(sourceId), false));
    }

    @Override
    public com.wensaas.core.api.menu.MenuQueryService.CatalogTree listPermissionCatalog() {
        // 租户域目录树（sys_menu 派生，与角色页同源）。模板跨租户、不做套餐门控：把全部受控模块当
        // 「已开通」传入，使 gate 永不命中 → 全量业务码可选（落到具体租户才被套餐 402 约束）。
        return menuQueryService.catalogTree(
                com.wensaas.core.api.menu.MenuQueryService.SCOPE_TENANT,
                packageGate.gatedModuleCodes());
    }

    @Override
    public List<PackageRef> listAvailablePackages() {
        return sysPackageMapper.selectList(
                Wrappers.<SysPackage>lambdaQuery()
                        .ne(SysPackage::getStatus, PackageAdminService.PackageStatus.ARCHIVED.code())
                        .orderByAsc(SysPackage::getCode))
                .stream()
                .map(p -> new PackageRef(p.getCode(), p.getName(),
                        PackageAdminService.PackageStatus.fromCode(p.getStatus()).name(),
                        Boolean.TRUE.equals(p.getIsSystem())))
                .toList();
    }

    // ===================== helpers =====================

    private List<String> reconcilePackages(SysRecommendedRoleTemplate t, List<String> desired) {
        Set<String> newSet = dedupRequirePackages(desired);
        Set<String> existing = new HashSet<>(loadPackageCodes(t.getId()));
        if (t.getStatus() == STATUS_PUBLISHED) {
            Set<String> removed = new HashSet<>(existing);
            removed.removeAll(newSet);
            if (!removed.isEmpty()) {
                throw new RecommendedRoleAdminException(
                        RecommendedRoleAdminException.Code.PACKAGE_REMOVE_FORBIDDEN,
                        "已发布模板禁止移除已关联的套餐包: " + new ArrayList<>(removed));
            }
        }
        Set<String> toAdd = new HashSet<>(newSet);
        toAdd.removeAll(existing);
        Set<String> toRemove = new HashSet<>(existing);
        toRemove.removeAll(newSet);
        if (!toAdd.isEmpty()) insertPackages(t.getId(), toAdd);
        if (!toRemove.isEmpty()) {
            packageMapper.delete(Wrappers.<SysRecommendedRoleTemplatePackage>lambdaQuery()
                    .eq(SysRecommendedRoleTemplatePackage::getTemplateId, t.getId())
                    .in(SysRecommendedRoleTemplatePackage::getPackageCode, toRemove));
        }
        List<String> result = new ArrayList<>(newSet);
        Collections.sort(result);
        return result;
    }

    private List<String> reconcilePermissions(SysRecommendedRoleTemplate t, List<String> desired) {
        Set<String> newSet = dedupValidatePermissions(desired);
        Set<String> existing = new HashSet<>(loadPermissionCodes(t.getId()));
        if (t.getStatus() == STATUS_PUBLISHED) {
            Set<String> removed = new HashSet<>(existing);
            removed.removeAll(newSet);
            if (!removed.isEmpty()) {
                throw new RecommendedRoleAdminException(
                        RecommendedRoleAdminException.Code.PERMISSION_REMOVE_FORBIDDEN,
                        "已发布模板禁止移除已包含的权限点: " + new ArrayList<>(removed));
            }
        }
        Set<String> toAdd = new HashSet<>(newSet);
        toAdd.removeAll(existing);
        Set<String> toRemove = new HashSet<>(existing);
        toRemove.removeAll(newSet);
        if (!toAdd.isEmpty()) insertPermissions(t.getId(), toAdd);
        if (!toRemove.isEmpty()) {
            permissionMapper.delete(Wrappers.<SysRecommendedRoleTemplatePermission>lambdaQuery()
                    .eq(SysRecommendedRoleTemplatePermission::getTemplateId, t.getId())
                    .in(SysRecommendedRoleTemplatePermission::getPermissionCode, toRemove));
        }
        List<String> result = new ArrayList<>(newSet);
        Collections.sort(result);
        return result;
    }

    private SysRecommendedRoleTemplate requireTemplate(long id) {
        SysRecommendedRoleTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new RecommendedRoleAdminException(
                    RecommendedRoleAdminException.Code.TEMPLATE_NOT_FOUND, "推荐角色模板不存在: " + id);
        }
        return t;
    }

    /** 去重 + 校验非空 + 校验套餐包存在；返回去重集合。 */
    private Set<String> dedupRequirePackages(List<String> codes) {
        Set<String> set = codes == null ? Set.of()
                : codes.stream().filter(c -> c != null && !c.isBlank()).collect(Collectors.toSet());
        if (set.isEmpty()) {
            throw new RecommendedRoleAdminException(
                    RecommendedRoleAdminException.Code.NO_PACKAGE_ASSOCIATED, "需关联至少 1 个套餐包");
        }
        Set<String> existing = sysPackageMapper.selectList(
                Wrappers.<SysPackage>lambdaQuery().in(SysPackage::getCode, set))
                .stream().map(SysPackage::getCode).collect(Collectors.toSet());
        for (String c : set) {
            if (!existing.contains(c)) {
                throw new RecommendedRoleAdminException(
                        RecommendedRoleAdminException.Code.PACKAGE_NOT_FOUND, "套餐包不存在: " + c);
            }
        }
        return set;
    }

    /** 去重 + 校验权限点已注册且非 ops.*；返回去重集合（允许空集）。 */
    private Set<String> dedupValidatePermissions(List<String> codes) {
        if (codes == null || codes.isEmpty()) return Set.of();
        Set<String> validNonOps = permissionRegistry.getAll().stream()
                .map(PermissionDef::code)
                .filter(c -> c != null && !c.startsWith(OPS_PERM_PREFIX))
                .collect(Collectors.toSet());
        Set<String> dedup = new HashSet<>();
        List<String> bad = new ArrayList<>();
        for (String c : codes) {
            if (c == null || c.isBlank()) continue;
            if (!validNonOps.contains(c)) bad.add(c);
            else dedup.add(c);
        }
        if (!bad.isEmpty()) {
            throw new RecommendedRoleAdminException(
                    RecommendedRoleAdminException.Code.INVALID_PERMISSION_CODE,
                    "未知或属于运营 ops.* 的权限点: " + bad);
        }
        return dedup;
    }

    private static void validateDataScope(int dataScope) {
        if (dataScope < 1 || dataScope > 3) {
            throw new RecommendedRoleAdminException(
                    RecommendedRoleAdminException.Code.INVALID_DATA_SCOPE,
                    "默认数据范围仅支持 1=全部 / 2=本部门含下属 / 3=仅本人，收到: " + dataScope);
        }
    }

    private void insertPackages(Long templateId, Collection<String> codes) {
        for (String c : codes) {
            SysRecommendedRoleTemplatePackage row = new SysRecommendedRoleTemplatePackage();
            row.setTemplateId(templateId);
            row.setPackageCode(c);
            packageMapper.insert(row);
        }
    }

    private void insertPermissions(Long templateId, Collection<String> codes) {
        for (String c : codes) {
            SysRecommendedRoleTemplatePermission row = new SysRecommendedRoleTemplatePermission();
            row.setTemplateId(templateId);
            row.setPermissionCode(c);
            permissionMapper.insert(row);
        }
    }

    private List<String> loadPackageCodes(long templateId) {
        return packageMapper.selectList(
                Wrappers.<SysRecommendedRoleTemplatePackage>lambdaQuery()
                        .eq(SysRecommendedRoleTemplatePackage::getTemplateId, templateId)
                        .orderByAsc(SysRecommendedRoleTemplatePackage::getPackageCode))
                .stream().map(SysRecommendedRoleTemplatePackage::getPackageCode).toList();
    }

    private List<String> loadPermissionCodes(long templateId) {
        return permissionMapper.selectList(
                Wrappers.<SysRecommendedRoleTemplatePermission>lambdaQuery()
                        .eq(SysRecommendedRoleTemplatePermission::getTemplateId, templateId)
                        .orderByAsc(SysRecommendedRoleTemplatePermission::getPermissionCode))
                .stream().map(SysRecommendedRoleTemplatePermission::getPermissionCode).toList();
    }

    private long countPackages(long templateId) {
        return packageMapper.selectCount(Wrappers.<SysRecommendedRoleTemplatePackage>lambdaQuery()
                .eq(SysRecommendedRoleTemplatePackage::getTemplateId, templateId));
    }

    private int countActivations(long templateId) {
        return activationMapper.selectCount(Wrappers.<SysRecommendedRoleTemplateActivation>lambdaQuery()
                .eq(SysRecommendedRoleTemplateActivation::getTemplateId, templateId)).intValue();
    }

    private static Long currentUserId() {
        UserPrincipal p = UserContext.get();
        return p != null ? p.userId() : null;
    }

    private static int statusFilterToInt(StatusFilter s) {
        return switch (s) {
            case DRAFT -> STATUS_DRAFT;
            case PUBLISHED -> STATUS_PUBLISHED;
            case INACTIVE -> STATUS_INACTIVE;
            case ALL -> -1;
        };
    }

    private static Instant toInstant(LocalDateTime dt) {
        return dt != null ? dt.toInstant(ZoneOffset.of("+08:00")) : null;
    }

    private TemplateDetail toDetail(SysRecommendedRoleTemplate t,
                                    List<String> packageCodes,
                                    List<String> permissionCodes,
                                    int activatedTenantCount) {
        List<AssociatedPackage> packages = buildPackages(packageCodes);
        return new TemplateDetail(
                t.getId(), t.getName(), t.getDescription(),
                TemplateStatus.fromCode(t.getStatus()), t.getDataScope(),
                packageCodes, permissionCodes,
                packages, invalidPermissions(permissionCodes), isAllArchived(packages),
                activatedTenantCount,
                toInstant(t.getCreatedAt()), toInstant(t.getUpdatedAt()));
    }

    /** 关联套餐包富信息：name/状态/基础标 + 每包已勾选租户数（OPS-R-021）+ 归档标记（OPS-R-023）。 */
    private List<AssociatedPackage> buildPackages(List<String> codes) {
        if (codes.isEmpty()) return List.of();
        Map<String, SysPackage> byCode = sysPackageMapper.selectList(
                        Wrappers.<SysPackage>lambdaQuery().in(SysPackage::getCode, codes))
                .stream().collect(Collectors.toMap(SysPackage::getCode, p -> p, (a, b) -> a));
        List<AssociatedPackage> result = new ArrayList<>(codes.size());
        for (String code : codes) {
            SysPackage p = byCode.get(code);
            boolean archived = p != null && p.getStatus() != null && p.getStatus() == PKG_ARCHIVED;
            String statusName = p != null && p.getStatus() != null
                    ? PackageAdminService.PackageStatus.fromCode(p.getStatus()).name() : null;
            result.add(new AssociatedPackage(
                    code,
                    p != null ? p.getName() : code,
                    p != null && Boolean.TRUE.equals(p.getIsSystem()),
                    statusName,
                    archived,
                    countTenantsHolding(code)));
        }
        return result;
    }

    /** 关联套餐包是否全部已归档（非空且全 ARCHIVED）。 */
    private static boolean isAllArchived(List<AssociatedPackage> packages) {
        return !packages.isEmpty() && packages.stream().allMatch(AssociatedPackage::archived);
    }

    /** 已落库但当前未在权限注册表中的权限点（OPS-R-022：业务模块卸载所致的失效）。 */
    private List<String> invalidPermissions(List<String> permissionCodes) {
        return permissionCodes.stream()
                .filter(c -> permissionRegistry.findByKey(c).isEmpty())
                .sorted()
                .toList();
    }

    /** 反查持有（未撤销）某套餐的租户数（复用 OPS-P-011 同款查询）。 */
    private int countTenantsHolding(String packageCode) {
        return (int) tenantOpsService.listTenants(
                1, 1, null, null, null, null, List.of(packageCode), null).total();
    }

    private static String csmDisplayName(SysUser u) {
        return u.getRealName();
    }
}
