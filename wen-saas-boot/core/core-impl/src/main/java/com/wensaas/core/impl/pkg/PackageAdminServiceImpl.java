package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.namesource.EntityTypes;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.api.pkg.PackageAdminException;
import com.wensaas.core.api.pkg.PackageAdminService;
import com.wensaas.core.impl.spi.module.SysBusinessModule;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@link PackageAdminService} 默认实现：套餐草稿 / 生效 / 停用 / 归档全生命周期与模块级勾选维护。
 */
public class PackageAdminServiceImpl implements PackageAdminService {

    static final int STATUS_DRAFT = 0;
    static final int STATUS_ACTIVE = 1;
    static final int STATUS_ARCHIVED = 2;
    static final int STATUS_INACTIVE = 3;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private final SysPackageMapper packageMapper;
    private final SysPackageModuleMapper packageModuleMapper;
    private final SysBusinessModuleMapper businessModuleMapper;
    private final ModuleApiRouteRegistry apiRouteRegistry;
    private final TenantOpsService tenantOpsService;

    public PackageAdminServiceImpl(SysPackageMapper packageMapper,
                                    SysPackageModuleMapper packageModuleMapper,
                                    SysBusinessModuleMapper businessModuleMapper,
                                    ModuleApiRouteRegistry apiRouteRegistry,
                                    TenantOpsService tenantOpsService) {
        this.packageMapper = packageMapper;
        this.packageModuleMapper = packageModuleMapper;
        this.businessModuleMapper = businessModuleMapper;
        this.apiRouteRegistry = apiRouteRegistry;
        this.tenantOpsService = tenantOpsService;
    }

    @Override
    public List<PackageListItem> listPackages(StatusFilter status, String keyword) {
        var q = Wrappers.<SysPackage>lambdaQuery();
        if (status != null && status != StatusFilter.ALL) {
            q.eq(SysPackage::getStatus, statusFilterToInt(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            q.and(w -> w.like(SysPackage::getCode, kw).or().like(SysPackage::getName, kw));
        }
        q.orderByAsc(SysPackage::getStatus).orderByDesc(SysPackage::getCreatedAt);
        List<SysPackage> rows = packageMapper.selectList(q);
        if (rows.isEmpty()) return List.of();

        List<Long> ids = rows.stream().map(SysPackage::getId).toList();
        Map<Long, Long> countByPkg = packageModuleMapper.selectList(
                Wrappers.<SysPackageModule>lambdaQuery().in(SysPackageModule::getPackageId, ids))
                .stream().collect(Collectors.groupingBy(SysPackageModule::getPackageId, Collectors.counting()));

        return rows.stream().map(p -> new PackageListItem(
                p.getCode(), p.getName(), p.getDescription(),
                PackageStatus.fromCode(p.getStatus()),
                p.getDefaultTtlDays(), p.getIsSystem(),
                countByPkg.getOrDefault(p.getId(), 0L).intValue(),
                toInstant(p.getCreatedAt()), toInstant(p.getUpdatedAt())
        )).toList();
    }

    @Override
    public PackageDetail getPackage(String code) {
        SysPackage pkg = requirePackage(code);
        List<String> moduleCodes = packageModuleMapper.selectList(
                Wrappers.<SysPackageModule>lambdaQuery()
                        .eq(SysPackageModule::getPackageId, pkg.getId())
                        .orderByAsc(SysPackageModule::getModuleCode))
                .stream().map(SysPackageModule::getModuleCode).toList();
        return toDetail(pkg, moduleCodes, countTenantsHolding(code));
    }

    @Override
    public List<BusinessModuleItem> listAvailableModules() {
        // 套餐候选只含真业务模块：以「声明了非空 apiPathPrefixes」判别
        // （底座虚拟模块 base 前缀为空、常驻不按套餐拦，排除在外）。
        return businessModuleMapper.selectList(
                Wrappers.<SysBusinessModule>lambdaQuery()
                        .orderByAsc(SysBusinessModule::getCode))
                .stream()
                .filter(m -> !apiRouteRegistry.prefixesOf(m.getCode()).isEmpty())
                .map(m -> new BusinessModuleItem(
                        m.getCode(), m.getName(), m.getDescription(), m.getIcon()))
                .toList();
    }

    @Override
    @Transactional
    public PackageDetail createPackage(CreatePackageCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        validateCode(cmd.code());
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("套餐名不能为空");
        }
        if (packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                .eq(SysPackage::getCode, cmd.code())) != null) {
            throw new PackageAdminException(PackageAdminException.Code.CODE_CONFLICT,
                    "套餐编码已存在: " + cmd.code());
        }

        Long operatorId = currentUserId();
        SysPackage pkg = new SysPackage();
        pkg.setCode(cmd.code());
        pkg.setName(cmd.name());
        pkg.setDescription(cmd.description());
        pkg.setIsSystem(false);
        pkg.setStatus(cmd.publishImmediately() ? STATUS_ACTIVE : STATUS_DRAFT);
        pkg.setDefaultTtlDays(cmd.defaultTtlDays());
        pkg.setCreatedBy(operatorId);
        pkg.setUpdatedBy(operatorId);
        packageMapper.insert(pkg);

        List<String> moduleCodes = cmd.moduleCodes() != null ? cmd.moduleCodes() : List.of();
        if (!moduleCodes.isEmpty()) {
            validateModulesExist(moduleCodes);
            insertModules(pkg.getId(), new HashSet<>(moduleCodes));
        }

        List<String> sortedModules = new ArrayList<>(new HashSet<>(moduleCodes));
        Collections.sort(sortedModules);
        return toDetail(pkg, sortedModules, 0); // 新建包尚无租户持有
    }

    @Override
    @Transactional
    public PackageDetail updatePackage(String code, UpdatePackageCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        SysPackage pkg = requirePackage(code);
        // ② 审计对象名：加载后即登记「旧名/新名」（业务前，失败也留意图）
        AuditObjects.add(EntityTypes.SYS_PACKAGE, code, pkg.getName(), cmd.name());
        boolean isBasic = Boolean.TRUE.equals(pkg.getIsSystem());

        if (isBasic && (cmd.defaultTtlDays() != null || cmd.moduleCodes() != null)) {
            throw new PackageAdminException(
                    PackageAdminException.Code.BASIC_PACKAGE_PROTECTED,
                    "基础包仅允许编辑 name 与 description");
        }

        boolean dirty = false;
        if (cmd.name() != null && !cmd.name().isBlank()) {
            pkg.setName(cmd.name());
            dirty = true;
        }
        if (cmd.description() != null) {
            pkg.setDescription(cmd.description());
            dirty = true;
        }
        if (!isBasic && cmd.defaultTtlDays() != null) {
            pkg.setDefaultTtlDays(cmd.defaultTtlDays());
            dirty = true;
        }
        if (dirty) {
            pkg.setUpdatedBy(currentUserId());
            packageMapper.updateById(pkg);
        }

        List<String> finalModules;
        if (!isBasic && cmd.moduleCodes() != null) {
            finalModules = reconcileModules(pkg, cmd.moduleCodes());
        } else {
            finalModules = packageModuleMapper.selectList(
                    Wrappers.<SysPackageModule>lambdaQuery()
                            .eq(SysPackageModule::getPackageId, pkg.getId())
                            .orderByAsc(SysPackageModule::getModuleCode))
                    .stream().map(SysPackageModule::getModuleCode).toList();
        }
        return toDetail(pkg, finalModules, countTenantsHolding(code));
    }

    @Override
    @Transactional
    public void publish(String code) {
        SysPackage pkg = requirePackage(code);
        AuditObjects.add(EntityTypes.SYS_PACKAGE, code, pkg.getName());
        int s = pkg.getStatus();
        if (s == STATUS_ARCHIVED) {
            throw new PackageAdminException(
                    PackageAdminException.Code.INVALID_STATUS_TRANSITION,
                    "已归档套餐不可上架");
        }
        if (s == STATUS_ACTIVE) return;
        pkg.setStatus(STATUS_ACTIVE);
        pkg.setUpdatedBy(currentUserId());
        packageMapper.updateById(pkg);
    }

    @Override
    @Transactional
    public void unpublish(String code) {
        SysPackage pkg = requirePackage(code);
        AuditObjects.add(EntityTypes.SYS_PACKAGE, code, pkg.getName());
        if (Boolean.TRUE.equals(pkg.getIsSystem())) {
            throw new PackageAdminException(
                    PackageAdminException.Code.BASIC_PACKAGE_PROTECTED, "基础包不可下架");
        }
        if (pkg.getStatus() != STATUS_ACTIVE) {
            throw new PackageAdminException(
                    PackageAdminException.Code.INVALID_STATUS_TRANSITION, "仅上架状态可下架");
        }
        pkg.setStatus(STATUS_INACTIVE);
        pkg.setUpdatedBy(currentUserId());
        packageMapper.updateById(pkg);
    }

    @Override
    @Transactional
    public void archive(String code) {
        SysPackage pkg = requirePackage(code);
        AuditObjects.add(EntityTypes.SYS_PACKAGE, code, pkg.getName());
        if (Boolean.TRUE.equals(pkg.getIsSystem())) {
            throw new PackageAdminException(
                    PackageAdminException.Code.BASIC_PACKAGE_PROTECTED, "基础包不可归档");
        }
        if (pkg.getStatus() != STATUS_INACTIVE) {
            throw new PackageAdminException(
                    PackageAdminException.Code.INVALID_STATUS_TRANSITION, "仅下架状态可归档");
        }
        pkg.setStatus(STATUS_ARCHIVED);
        pkg.setUpdatedBy(currentUserId());
        packageMapper.updateById(pkg);
    }

    @Override
    @Transactional
    public void deletePackage(String code) {
        SysPackage pkg = requirePackage(code);
        AuditObjects.add(EntityTypes.SYS_PACKAGE, code, pkg.getName());
        if (Boolean.TRUE.equals(pkg.getIsSystem())) {
            throw new PackageAdminException(
                    PackageAdminException.Code.BASIC_PACKAGE_PROTECTED, "基础包不可删除");
        }
        if (pkg.getStatus() != STATUS_DRAFT) {
            throw new PackageAdminException(
                    PackageAdminException.Code.INVALID_STATUS_TRANSITION, "仅草稿状态可删除");
        }
        packageModuleMapper.delete(Wrappers.<SysPackageModule>lambdaQuery()
                .eq(SysPackageModule::getPackageId, pkg.getId()));
        packageMapper.deleteById(pkg.getId());
    }

    @Override
    @Transactional
    public PackageDetail copyPackage(String sourceCode, CopyPackageCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        SysPackage source = requirePackage(sourceCode);
        // 拷源的模块勾选（基础包此表无行 → 拷出空集合的普通草稿包）
        List<String> sourceModules = packageModuleMapper.selectList(
                Wrappers.<SysPackageModule>lambdaQuery()
                        .eq(SysPackageModule::getPackageId, source.getId()))
                .stream().map(SysPackageModule::getModuleCode).toList();
        // 复用创建路径：恒为草稿（publishImmediately=false）
        return createPackage(new CreatePackageCommand(
                cmd.code(), cmd.name(), source.getDescription(),
                source.getDefaultTtlDays(), sourceModules, false));
    }

    // ===================== helpers =====================

    private List<String> reconcileModules(SysPackage pkg, List<String> desired) {
        List<SysPackageModule> existingRows = packageModuleMapper.selectList(
                Wrappers.<SysPackageModule>lambdaQuery().eq(SysPackageModule::getPackageId, pkg.getId()));
        Set<String> existingSet = existingRows.stream()
                .map(SysPackageModule::getModuleCode).collect(Collectors.toSet());
        Set<String> newSet = new HashSet<>(desired);

        if (pkg.getStatus() == STATUS_ACTIVE) {
            Set<String> removed = new HashSet<>(existingSet);
            removed.removeAll(newSet);
            if (!removed.isEmpty()) {
                throw new PackageAdminException(
                        PackageAdminException.Code.MODULE_REMOVE_FORBIDDEN,
                        "上架后禁止移除已包含的模块: " + new ArrayList<>(removed));
            }
        }

        Set<String> toAdd = new HashSet<>(newSet);
        toAdd.removeAll(existingSet);
        Set<String> toRemove = new HashSet<>(existingSet);
        toRemove.removeAll(newSet);

        if (!toAdd.isEmpty()) {
            validateModulesExist(new ArrayList<>(toAdd));
            insertModules(pkg.getId(), toAdd);
        }
        if (!toRemove.isEmpty()) {
            packageModuleMapper.delete(Wrappers.<SysPackageModule>lambdaQuery()
                    .eq(SysPackageModule::getPackageId, pkg.getId())
                    .in(SysPackageModule::getModuleCode, toRemove));
        }
        List<String> result = new ArrayList<>(newSet);
        Collections.sort(result);
        return result;
    }

    private SysPackage requirePackage(String code) {
        SysPackage pkg = packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                .eq(SysPackage::getCode, code));
        if (pkg == null) {
            throw new PackageAdminException(
                    PackageAdminException.Code.PACKAGE_NOT_FOUND, "套餐不存在: " + code);
        }
        return pkg;
    }

    private static void validateCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "套餐编码不合法（要求 ^[A-Z][A-Z0-9_]{1,63}$）: " + code);
        }
    }

    private void validateModulesExist(List<String> codes) {
        Set<String> existing = businessModuleMapper.selectList(
                Wrappers.<SysBusinessModule>lambdaQuery().in(SysBusinessModule::getCode, codes))
                .stream().map(SysBusinessModule::getCode).collect(Collectors.toSet());
        for (String c : codes) {
            if (!existing.contains(c)) {
                throw new PackageAdminException(
                        PackageAdminException.Code.MODULE_NOT_FOUND, "业务模块不存在: " + c);
            }
        }
    }

    private void insertModules(Long pkgId, Collection<String> codes) {
        for (String c : codes) {
            SysPackageModule row = new SysPackageModule();
            row.setPackageId(pkgId);
            row.setModuleCode(c);
            packageModuleMapper.insert(row);
        }
    }

    private static Long currentUserId() {
        UserPrincipal p = UserContext.get();
        return p != null ? p.userId() : null;
    }

    private static int statusFilterToInt(StatusFilter s) {
        return switch (s) {
            case DRAFT -> STATUS_DRAFT;
            case ACTIVE -> STATUS_ACTIVE;
            case INACTIVE -> STATUS_INACTIVE;
            case ARCHIVED -> STATUS_ARCHIVED;
            case ALL -> -1;
        };
    }

    private static Instant toInstant(LocalDateTime dt) {
        return dt != null ? dt.toInstant(ZoneOffset.of("+08:00")) : null;
    }

    /** 反查持有（未撤销）某套餐的租户数（复用 OPS-P-011 同款查询，与推荐角色 packages[].tenantCount 一致）。 */
    private int countTenantsHolding(String code) {
        return (int) tenantOpsService.listTenants(
                1, 1, null, null, null, null, List.of(code), null).total();
    }

    private static PackageDetail toDetail(SysPackage pkg, List<String> moduleCodes, int tenantCount) {
        return new PackageDetail(
                pkg.getCode(), pkg.getName(), pkg.getDescription(),
                PackageStatus.fromCode(pkg.getStatus()),
                pkg.getDefaultTtlDays(), pkg.getIsSystem(),
                moduleCodes, tenantCount,
                toInstant(pkg.getCreatedAt()), toInstant(pkg.getUpdatedAt()));
    }
}
