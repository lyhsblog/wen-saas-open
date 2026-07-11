package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantPlan;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.user.CapabilitiesService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析租户已勾选套餐 → 激活模块 / 仅过期模块 / packageViews。
 *
 * <p>v1 数据来源 {@code sys_tenant.plans jsonb} + {@code sys_package} + {@code sys_package_module}。
 *
 * <p>规则：
 * <ul>
 *   <li>撤销的套餐（{@code revokedAt != null}）整体不返回（立即对租户不可见）</li>
 *   <li>过期未撤销的套餐仍在 packageViews 内（让用户看到为何只读），readonly=true</li>
 *   <li>套餐到期 → 功能只读：仅当 module 仅出现在过期套餐内、不在任何 active 套餐内时，readonly=true</li>
 *   <li>多套餐覆盖同一模块时取最宽松：只要任一 active 套餐含该模块，readonly=false</li>
 * </ul>
 *
 * <p>所涉表均在 {@code wen-saas-boot.tenant.platform-only-tables} 白名单内，MP TenantLine 自动跳过，
 * 无需 {@code @CrossTenantQuery}。
 */
public class TenantPlansResolver {

    private final TenantQueryService tenantQuery;
    private final SysPackageMapper packageMapper;
    private final SysPackageModuleMapper packageModuleMapper;

    public TenantPlansResolver(TenantQueryService tenantQuery,
                               SysPackageMapper packageMapper,
                               SysPackageModuleMapper packageModuleMapper) {
        this.tenantQuery = tenantQuery;
        this.packageMapper = packageMapper;
        this.packageModuleMapper = packageModuleMapper;
    }

    public ResolvedPlans resolve(long tenantId) {
        List<TenantPlan> plans = tenantQuery.plansOf(tenantId);
        if (plans.isEmpty()) {
            return ResolvedPlans.empty();
        }

        Instant now = Instant.now();
        Map<String, TenantPlan> activeByPkg = new LinkedHashMap<>();
        Map<String, TenantPlan> expiredByPkg = new LinkedHashMap<>();
        for (TenantPlan plan : plans) {
            if (plan == null || plan.packageCode() == null || plan.revokedAt() != null) {
                continue;
            }
            if (plan.isActive(now)) {
                activeByPkg.put(plan.packageCode(), plan);
            } else if (plan.isExpiredOnly(now)) {
                expiredByPkg.put(plan.packageCode(), plan);
            }
        }
        if (activeByPkg.isEmpty() && expiredByPkg.isEmpty()) {
            return ResolvedPlans.empty();
        }

        Set<String> involvedCodes = new HashSet<>();
        involvedCodes.addAll(activeByPkg.keySet());
        involvedCodes.addAll(expiredByPkg.keySet());

        List<SysPackage> packages = packageMapper.selectList(
                new LambdaQueryWrapper<SysPackage>().in(SysPackage::getCode, involvedCodes));
        Map<String, SysPackage> packageByCode = new HashMap<>(packages.size());
        Map<Long, String> codeByPackageId = new HashMap<>(packages.size());
        List<Long> packageIds = new ArrayList<>(packages.size());
        for (SysPackage pkg : packages) {
            packageByCode.put(pkg.getCode(), pkg);
            codeByPackageId.put(pkg.getId(), pkg.getCode());
            packageIds.add(pkg.getId());
        }

        Map<String, Set<String>> modulesByPackageCode = new HashMap<>();
        if (!packageIds.isEmpty()) {
            List<SysPackageModule> rows = packageModuleMapper.selectList(
                    new LambdaQueryWrapper<SysPackageModule>().in(SysPackageModule::getPackageId, packageIds));
            for (SysPackageModule row : rows) {
                String code = codeByPackageId.get(row.getPackageId());
                if (code != null) {
                    modulesByPackageCode.computeIfAbsent(code, k -> new HashSet<>()).add(row.getModuleCode());
                }
            }
        }

        Set<String> activeModuleCodes = new HashSet<>();
        for (String pkgCode : activeByPkg.keySet()) {
            activeModuleCodes.addAll(modulesByPackageCode.getOrDefault(pkgCode, Set.of()));
        }
        Set<String> readonlyModuleCodes = new HashSet<>();
        for (String pkgCode : expiredByPkg.keySet()) {
            for (String moduleCode : modulesByPackageCode.getOrDefault(pkgCode, Set.of())) {
                if (!activeModuleCodes.contains(moduleCode)) {
                    readonlyModuleCodes.add(moduleCode);
                }
            }
        }

        List<CapabilitiesService.PackageView> packageViews = new ArrayList<>();
        for (Map.Entry<String, TenantPlan> e : activeByPkg.entrySet()) {
            packageViews.add(toView(e.getValue(), packageByCode, false));
        }
        for (Map.Entry<String, TenantPlan> e : expiredByPkg.entrySet()) {
            packageViews.add(toView(e.getValue(), packageByCode, true));
        }
        packageViews.sort(Comparator.comparing(CapabilitiesService.PackageView::code));

        return new ResolvedPlans(activeModuleCodes, readonlyModuleCodes, packageViews);
    }

    private CapabilitiesService.PackageView toView(TenantPlan plan,
                                                    Map<String, SysPackage> packageByCode,
                                                    boolean readonly) {
        SysPackage pkg = packageByCode.get(plan.packageCode());
        String name = pkg != null ? pkg.getName() : plan.packageCode();
        return new CapabilitiesService.PackageView(plan.packageCode(), name, plan.expiresAt(), readonly);
    }

    /**
     * 解析结果。
     *
     * @param activeModuleCodes   active 套餐内的模块编码集合
     * @param readonlyModuleCodes 仅出现在过期套餐内的模块编码（active 集合不含时）
     * @param packageViews        所有未撤销套餐的视图（含过期 readonly=true），按 code 排序
     */
    public record ResolvedPlans(Set<String> activeModuleCodes,
                                Set<String> readonlyModuleCodes,
                                List<CapabilitiesService.PackageView> packageViews) {

        public static ResolvedPlans empty() {
            return new ResolvedPlans(Set.of(), Set.of(), List.of());
        }
    }
}
