package com.wensaas.core.impl.pkg;

import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 套餐模块可见性门控。统一回答「某权限码是否因租户未开通其所属受控业务模块而应隐藏」。
 *
 * <p>受控模块 = 声明了非空 API 前缀的业务模块（{@link ModuleApiRouteRegistry#gatedModuleCodes()}，即 S4
 * 真拦截集）；底座模块（前缀空）/ ops 不在其中，永远可见。一处判定供三处复用：
 * <ul>
 *   <li>导航树 / 角色目录树（{@code MenuQueryService}）—— 未购模块菜单与可授码整段隐去；</li>
 *   <li>角色权限保存（{@code RoleAdminService}）—— 保存只增删可见码，对当前不可见的历史码原样保留，
 *       避免一次保存静默吊销退订前的授权。</li>
 * </ul>
 */
@Component
public class PackageModuleGate {

    private final PermissionRegistry permissionRegistry;
    private final ModuleApiRouteRegistry apiRouteRegistry;
    private final TenantPlansResolver tenantPlansResolver;

    public PackageModuleGate(PermissionRegistry permissionRegistry,
                             ModuleApiRouteRegistry apiRouteRegistry,
                             TenantPlansResolver tenantPlansResolver) {
        this.permissionRegistry = permissionRegistry;
        this.apiRouteRegistry = apiRouteRegistry;
        this.tenantPlansResolver = tenantPlansResolver;
    }

    /** 租户已开通（active ∪ 过期只读）的业务模块编码集合——过期只读仍属持有、可继续分配角色。 */
    public Set<String> enabledModuleCodes(long tenantId) {
        TenantPlansResolver.ResolvedPlans plans = tenantPlansResolver.resolve(tenantId);
        Set<String> out = new HashSet<>(plans.activeModuleCodes());
        out.addAll(plans.readonlyModuleCodes());
        return out;
    }

    /** 受套餐控制的业务模块编码集合（S4 真拦截集）。一次取出、供树遍历批量判定复用。 */
    public Set<String> gatedModuleCodes() {
        return apiRouteRegistry.gatedModuleCodes();
    }

    /** {@code code} 属主是受控模块且 {@code enabledModuleCodes} 不含该模块时返回 true（应隐藏）。 */
    public boolean hidden(String code, Set<String> enabledModuleCodes) {
        return hidden(code, enabledModuleCodes, gatedModuleCodes());
    }

    /** 纯判定重载：{@code gated} 由调用方预取（树遍历时避免逐码重算）。 */
    public boolean hidden(String code, Set<String> enabledModuleCodes, Set<String> gated) {
        if (code == null) {
            return false;
        }
        String owner = permissionRegistry.findOwnerModule(code).orElse(null);
        return owner != null && gated.contains(owner) && !enabledModuleCodes.contains(owner);
    }
}
