package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantPlan;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantPlansResolverTest {

    private static final long TENANT_ID = 9001L;

    private TenantQueryService tenantQuery;
    private SysPackageMapper packageMapper;
    private SysPackageModuleMapper packageModuleMapper;
    private TenantPlansResolver resolver;

    @BeforeEach
    void setup() {
        tenantQuery = mock(TenantQueryService.class);
        packageMapper = mock(SysPackageMapper.class);
        packageModuleMapper = mock(SysPackageModuleMapper.class);
        when(tenantQuery.plansOf(eq(TENANT_ID))).thenReturn(List.of());
        resolver = new TenantPlansResolver(tenantQuery, packageMapper, packageModuleMapper);
    }

    @Test
    void resolve_zeroOrNegativeTenantId_returnsEmpty() {
        assertThat(resolver.resolve(0L)).isEqualTo(TenantPlansResolver.ResolvedPlans.empty());
        assertThat(resolver.resolve(-1L)).isEqualTo(TenantPlansResolver.ResolvedPlans.empty());
    }

    @Test
    void resolve_tenantNotFound_returnsEmpty() {
        when(tenantQuery.plansOf(eq(TENANT_ID))).thenReturn(List.of());

        assertThat(resolver.resolve(TENANT_ID)).isEqualTo(TenantPlansResolver.ResolvedPlans.empty());
    }

    @Test
    void resolve_emptyPlans_returnsEmpty() {
        when(tenantQuery.plansOf(eq(TENANT_ID))).thenReturn(List.of());

        assertThat(resolver.resolve(TENANT_ID)).isEqualTo(TenantPlansResolver.ResolvedPlans.empty());
    }

    @Test
    void resolve_activePlanMappedToModuleCodes() {
        Instant now = Instant.now();
        when(tenantQuery.plansOf(eq(TENANT_ID))).thenReturn(List.of(
                new TenantPlan("crm-pro", iso(now.minus(Duration.ofDays(10))), null, null)));

        SysPackage pkg = newPackage(2001L, "crm-pro", "CRM 专业版");
        when(packageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(pkg));
        when(packageModuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                newPackageModule(2001L, "crm"),
                newPackageModule(2001L, "marketing")));

        TenantPlansResolver.ResolvedPlans resolved = resolver.resolve(TENANT_ID);

        assertThat(resolved.activeModuleCodes()).containsExactlyInAnyOrder("crm", "marketing");
        assertThat(resolved.readonlyModuleCodes()).isEmpty();
        assertThat(resolved.packageViews()).hasSize(1);
        assertThat(resolved.packageViews().get(0).code()).isEqualTo("crm-pro");
        assertThat(resolved.packageViews().get(0).readonly()).isFalse();
    }

    @Test
    void resolve_expiredPlan_modulesGoToReadonly() {
        Instant now = Instant.now();
        when(tenantQuery.plansOf(eq(TENANT_ID))).thenReturn(List.of(
                new TenantPlan("crm-trial",
                        iso(now.minus(Duration.ofDays(60))), iso(now.minus(Duration.ofDays(1))), null)));
        when(packageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                newPackage(2002L, "crm-trial", "CRM 试用")));
        when(packageModuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                newPackageModule(2002L, "crm")));

        TenantPlansResolver.ResolvedPlans resolved = resolver.resolve(TENANT_ID);

        assertThat(resolved.activeModuleCodes()).isEmpty();
        assertThat(resolved.readonlyModuleCodes()).containsExactly("crm");
        assertThat(resolved.packageViews()).hasSize(1);
        assertThat(resolved.packageViews().get(0).readonly()).isTrue();
    }

    @Test
    void resolve_revokedPlan_excluded() {
        Instant now = Instant.now();
        List<TenantPlan> plans = new ArrayList<>();
        plans.add(new TenantPlan("crm-pro",
                iso(now.minus(Duration.ofDays(30))), null,
                iso(now.minus(Duration.ofDays(5)))));
        when(tenantQuery.plansOf(eq(TENANT_ID))).thenReturn(plans);

        TenantPlansResolver.ResolvedPlans resolved = resolver.resolve(TENANT_ID);

        assertThat(resolved.activeModuleCodes()).isEmpty();
        assertThat(resolved.readonlyModuleCodes()).isEmpty();
        assertThat(resolved.packageViews()).isEmpty();
    }

    @Test
    void resolve_overlappingActiveAndExpired_activeWins() {
        Instant now = Instant.now();
        when(tenantQuery.plansOf(eq(TENANT_ID))).thenReturn(List.of(
                new TenantPlan("crm-pro", iso(now.minus(Duration.ofDays(10))), null, null),
                new TenantPlan("crm-trial",
                        iso(now.minus(Duration.ofDays(90))), iso(now.minus(Duration.ofDays(1))), null)));
        when(packageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                newPackage(2001L, "crm-pro", "CRM 专业版"),
                newPackage(2002L, "crm-trial", "CRM 试用")));
        when(packageModuleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                newPackageModule(2001L, "crm"),
                newPackageModule(2002L, "crm")));

        TenantPlansResolver.ResolvedPlans resolved = resolver.resolve(TENANT_ID);

        assertThat(resolved.activeModuleCodes()).containsExactly("crm");
        // crm 在 active 套餐内，不应进 readonly
        assertThat(resolved.readonlyModuleCodes()).isEmpty();
        // packageViews 含两个，按 code 排序
        assertThat(resolved.packageViews()).hasSize(2);
        assertThat(resolved.packageViews().get(0).code()).isEqualTo("crm-pro");
        assertThat(resolved.packageViews().get(0).readonly()).isFalse();
        assertThat(resolved.packageViews().get(1).code()).isEqualTo("crm-trial");
        assertThat(resolved.packageViews().get(1).readonly()).isTrue();
    }

    private static String iso(Instant t) {
        return DateTimeFormatter.ISO_INSTANT.format(t);
    }

    private SysPackage newPackage(long id, String code, String name) {
        SysPackage p = new SysPackage();
        p.setId(id);
        p.setCode(code);
        p.setName(name);
        p.setIsSystem(false);
        p.setStatus(1);
        return p;
    }

    private SysPackageModule newPackageModule(long packageId, String moduleCode) {
        SysPackageModule pm = new SysPackageModule();
        pm.setPackageId(packageId);
        pm.setModuleCode(moduleCode);
        return pm;
    }
}
