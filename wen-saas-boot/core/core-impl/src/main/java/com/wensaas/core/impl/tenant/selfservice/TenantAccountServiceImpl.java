package com.wensaas.core.impl.tenant.selfservice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.auth.user.CapabilitiesService;
import com.wensaas.core.api.auth.user.MemberCountQuery;
import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.api.tenant.selfservice.TenantAccountService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.ops.SysTenantSeatAddon;
import com.wensaas.core.impl.ops.SysTenantSeatAddonMapper;
import com.wensaas.core.impl.pkg.SysPackage;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.impl.pkg.SysPackageModule;
import com.wensaas.core.impl.pkg.SysPackageModuleMapper;
import com.wensaas.core.impl.pkg.TenantPlansResolver;
import com.wensaas.core.impl.spi.module.SysBusinessModule;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;

/**
 * 租户套餐与账号只读面板实现。
 *
 * <p>本服务运行在当前租户上下文内（{@link TenantContext#require()}）。套餐 / 加购汇总走当前租户
 * 自身数据；变更历史委托 {@link TenantOpsService#listTenantEvents}（其内部 {@code @CrossTenantQuery}
 * 读运营操作日志，按 target_tenant_id 命中本租户）。
 */
public class TenantAccountServiceImpl implements TenantAccountService {

    private static final int EXPIRING_SOON_DAYS = 30;

    private final SysTenantMapper tenantMapper;
    private final SysTenantSeatAddonMapper seatAddonMapper;
    private final MemberCountQuery memberCount;
    private final SysPackageMapper packageMapper;
    private final SysPackageModuleMapper packageModuleMapper;
    private final SysBusinessModuleMapper businessModuleMapper;
    private final TenantPlansResolver plansResolver;
    private final TenantOpsService tenantOpsService;
    private final String supportPhone;
    private final String supportEmail;

    public TenantAccountServiceImpl(SysTenantMapper tenantMapper,
                                    SysTenantSeatAddonMapper seatAddonMapper,
                                    MemberCountQuery memberCount,
                                    SysPackageMapper packageMapper,
                                    SysPackageModuleMapper packageModuleMapper,
                                    SysBusinessModuleMapper businessModuleMapper,
                                    TenantPlansResolver plansResolver,
                                    TenantOpsService tenantOpsService,
                                    String supportPhone,
                                    String supportEmail) {
        this.tenantMapper = tenantMapper;
        this.seatAddonMapper = seatAddonMapper;
        this.memberCount = memberCount;
        this.packageMapper = packageMapper;
        this.packageModuleMapper = packageModuleMapper;
        this.businessModuleMapper = businessModuleMapper;
        this.plansResolver = plansResolver;
        this.tenantOpsService = tenantOpsService;
        this.supportPhone = supportPhone;
        this.supportEmail = supportEmail;
    }

    @Override
    public AccountOverview getOverview() {
        long tenantId = TenantContext.require();
        SysTenant tenant = tenantMapper.selectById(tenantId);

        List<CapabilitiesService.PackageView> views = plansResolver.resolve(tenantId).packageViews();
        List<PlanItem> plans = enrichPlans(views);

        Instant now = Instant.now();
        int expiringSoon = 0;
        Instant nearest = null;
        for (PlanItem p : plans) {
            if (p.expiresAt() == null) {
                continue;
            }
            if (nearest == null || p.expiresAt().isBefore(nearest)) {
                nearest = p.expiresAt();
            }
            if (!p.readonly() && p.expiresAt().isAfter(now)
                    && p.expiresAt().isBefore(now.plus(EXPIRING_SOON_DAYS, ChronoUnit.DAYS))) {
                expiringSoon++;
            }
        }

        List<SysTenantSeatAddon> addons = seatAddonMapper.selectList(
                Wrappers.<SysTenantSeatAddon>lambdaQuery().eq(SysTenantSeatAddon::getTenantId, tenantId));
        int addonSeats = addons.stream()
                .mapToInt(a -> a.getQuantity() != null ? a.getQuantity() : 0)
                .sum();
        long usedSeats = memberCount.countActiveMembers(tenantId);

        return new AccountOverview(
                String.valueOf(tenantId),
                tenant != null ? tenant.getTenantNo() : null,
                plans.size(),
                expiringSoon,
                tenant != null ? tenant.getMemberCap() : null,
                addonSeats,
                usedSeats,
                nearest,
                plans,
                new SupportContact(supportPhone, supportEmail));
    }

    /** PackageView（code/name/expiresAt 字符串/readonly）→ PlanItem，补 systemDefault + 功能模块清单。 */
    private List<PlanItem> enrichPlans(List<CapabilitiesService.PackageView> views) {
        if (views.isEmpty()) {
            return List.of();
        }
        Set<String> codes = new java.util.HashSet<>();
        views.forEach(v -> codes.add(v.code()));

        List<SysPackage> packages = packageMapper.selectList(
                new LambdaQueryWrapper<SysPackage>().in(SysPackage::getCode, codes));
        Map<String, SysPackage> pkgByCode = new HashMap<>();
        Map<Long, String> codeByPkgId = new HashMap<>();
        List<Long> pkgIds = new ArrayList<>();
        for (SysPackage pkg : packages) {
            pkgByCode.put(pkg.getCode(), pkg);
            codeByPkgId.put(pkg.getId(), pkg.getCode());
            pkgIds.add(pkg.getId());
        }

        // package code -> module codes
        Map<String, List<String>> moduleCodesByPkg = new HashMap<>();
        Set<String> allModuleCodes = new java.util.HashSet<>();
        if (!pkgIds.isEmpty()) {
            List<SysPackageModule> rows = packageModuleMapper.selectList(
                    new LambdaQueryWrapper<SysPackageModule>().in(SysPackageModule::getPackageId, pkgIds));
            for (SysPackageModule row : rows) {
                String code = codeByPkgId.get(row.getPackageId());
                if (code != null) {
                    moduleCodesByPkg.computeIfAbsent(code, k -> new ArrayList<>()).add(row.getModuleCode());
                    allModuleCodes.add(row.getModuleCode());
                }
            }
        }
        Map<String, String> moduleNameByCode = new HashMap<>();
        if (!allModuleCodes.isEmpty()) {
            businessModuleMapper.selectList(
                    new LambdaQueryWrapper<SysBusinessModule>().in(SysBusinessModule::getCode, allModuleCodes))
                    .forEach(m -> moduleNameByCode.put(m.getCode(), m.getName()));
        }

        List<PlanItem> out = new ArrayList<>(views.size());
        for (CapabilitiesService.PackageView v : views) {
            SysPackage pkg = pkgByCode.get(v.code());
            boolean systemDefault = pkg != null && Boolean.TRUE.equals(pkg.getIsSystem());
            List<PlanModule> modules = moduleCodesByPkg.getOrDefault(v.code(), List.of()).stream()
                    .map(mc -> new PlanModule(mc, moduleNameByCode.getOrDefault(mc, mc)))
                    .toList();
            out.add(new PlanItem(
                    v.code(),
                    v.name(),
                    parseInstantOrNull(v.expiresAt()),
                    v.readonly(),
                    systemDefault,
                    modules));
        }
        // 永久（expiresAt null）→ 未到期（剩余天数升序）→ 已到期（readonly）
        out.sort((a, b) -> {
            int ra = rank(a);
            int rb = rank(b);
            if (ra != rb) {
                return Integer.compare(ra, rb);
            }
            if (ra == 1) { // 均为未到期，按到期日升序
                return a.expiresAt().compareTo(b.expiresAt());
            }
            return a.packageCode().compareTo(b.packageCode());
        });
        return out;
    }

    private static int rank(PlanItem p) {
        if (p.expiresAt() == null) {
            return 0; // 永久
        }
        return p.readonly() ? 2 : 1;
    }

    private static Instant parseInstantOrNull(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public ChangeHistoryPage listChangeHistory(int page, int pageSize) {
        long tenantId = TenantContext.require();
        TenantOpsService.TenantEventPage events = tenantOpsService.listTenantEvents(tenantId, page, pageSize);
        List<ChangeHistoryItem> items = events.items().stream()
                .map(e -> new ChangeHistoryItem(
                        e.id(), e.action(), e.objectType(), e.objectDesc(),
                        e.operatorName(), e.createdAt()))
                .toList();
        return new ChangeHistoryPage(items, events.total(), events.page(), events.pageSize());
    }
}
