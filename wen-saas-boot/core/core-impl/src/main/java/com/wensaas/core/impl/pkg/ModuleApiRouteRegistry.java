package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 业务模块 API 路径前缀索引（内存）。
 *
 * <p>由 {@code BusinessModuleRegistrar} 在启动期按模块声明的
 * {@code BusinessModuleDescriptor.apiPathPrefixes()} 填充；
 * 运行期由 {@link com.wensaas.core.impl.pkg.PackageStatusWebFilter} 查询。
 *
 * <p>支持最长前缀匹配，允许同一模块注册多个前缀（如 {@code /api/v1/crm/} + {@code /api/h5/crm/}）。
 * 不同模块注册同一前缀时，在 {@link #register} 阶段抛出 {@link BusinessModuleConflictException} 阻断启动。
 */
@Component
public class ModuleApiRouteRegistry {

    private final Map<String, String> prefixToCode = new LinkedHashMap<>();

    /**
     * 注册一个 API 前缀 → 模块编码的映射。
     *
     * @throws BusinessModuleConflictException 前缀已被另一模块占用时抛出
     */
    public synchronized void register(String prefix, String moduleCode) {
        String existing = prefixToCode.get(prefix);
        if (existing != null && !existing.equals(moduleCode)) {
            throw new BusinessModuleConflictException(
                    "API路径前缀冲突: \"" + prefix + "\" 已被模块 [" + existing
                            + "] 占用，与模块 [" + moduleCode + "] 冲突");
        }
        prefixToCode.put(prefix, moduleCode);
    }

    /**
     * 最长前缀匹配，返回请求路径所属业务模块编码。
     * 平台保留路径（/api/auth/、/api/v1/me/ 等）未注册，返回 {@link Optional#empty()}。
     */
    public synchronized Optional<String> findModuleCode(String requestPath) {
        String best = null;
        int bestLen = 0;
        for (Map.Entry<String, String> e : prefixToCode.entrySet()) {
            String prefix = e.getKey();
            if (requestPath.startsWith(prefix) && prefix.length() > bestLen) {
                best = e.getValue();
                bestLen = prefix.length();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * 返回声明了非空 API 前缀的业务模块编码集合，即受套餐真拦截（S4）约束的「受控模块」。
     *
     * <p>菜单/角色目录据此对未购模块做可见性门控：权限码属主 ∈ 本集合
     * 且租户未购该模块 → 该码在导航树/角色目录树中隐藏。底座模块（前缀空）不入此集合、永远可见。
     */
    public synchronized Set<String> gatedModuleCodes() {
        return new java.util.HashSet<>(prefixToCode.values());
    }

    /**
     * 返回某业务模块声明的全部 API 路径前缀。供运营「业务模块」管理页详情（X8）展示。
     */
    public synchronized List<String> prefixesOf(String moduleCode) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : prefixToCode.entrySet()) {
            if (e.getValue().equals(moduleCode)) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}
