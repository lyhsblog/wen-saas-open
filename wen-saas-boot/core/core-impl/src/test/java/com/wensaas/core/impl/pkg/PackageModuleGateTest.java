package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.auth.user.CapabilitiesService.PackageView;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PackageModuleGate} 单测：受控模块属主 + 租户开通集 → 隐藏判定与 enabledModuleCodes 合并（S9）。
 */
class PackageModuleGateTest {

    private PermissionRegistry permissionRegistry;
    private ModuleApiRouteRegistry apiRouteRegistry;
    private TenantPlansResolver plansResolver;
    private PackageModuleGate gate;

    @BeforeEach
    void setup() {
        permissionRegistry = mock(PermissionRegistry.class);
        apiRouteRegistry = mock(ModuleApiRouteRegistry.class);
        plansResolver = mock(TenantPlansResolver.class);
        gate = new PackageModuleGate(permissionRegistry, apiRouteRegistry, plansResolver);

        when(apiRouteRegistry.gatedModuleCodes()).thenReturn(Set.of("crm"));
        when(permissionRegistry.findOwnerModule("crm.customer.list")).thenReturn(Optional.of("crm"));
        when(permissionRegistry.findOwnerModule("org.member.list")).thenReturn(Optional.of("base"));
        when(permissionRegistry.findOwnerModule("ops.team.role.list")).thenReturn(Optional.of("ops"));
        when(permissionRegistry.findOwnerModule("orphan.code")).thenReturn(Optional.empty());
    }

    @Test
    void hidden_onlyWhenOwnerGatedAndNotEnabled() {
        // 受控模块 crm 未开通 → 隐藏
        assertThat(gate.hidden("crm.customer.list", Set.of())).isTrue();
        // 受控模块 crm 已开通 → 不隐藏
        assertThat(gate.hidden("crm.customer.list", Set.of("crm"))).isFalse();
        // 底座 / ops / 属主未知 / null 一律不隐藏
        assertThat(gate.hidden("org.member.list", Set.of())).isFalse();
        assertThat(gate.hidden("ops.team.role.list", Set.of())).isFalse();
        assertThat(gate.hidden("orphan.code", Set.of())).isFalse();
        assertThat(gate.hidden(null, Set.of())).isFalse();
    }

    @Test
    void enabledModuleCodes_mergesActiveAndReadonly() {
        when(plansResolver.resolve(7L)).thenReturn(new TenantPlansResolver.ResolvedPlans(
                Set.of("crm"), Set.of("ticket"), List.<PackageView>of()));

        assertThat(gate.enabledModuleCodes(7L)).containsExactlyInAnyOrder("crm", "ticket");
    }
}
