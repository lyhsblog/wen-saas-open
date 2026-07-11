package com.wensaas.core.impl.spi.module;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.impl.tenant.TenantProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务模块表归属与存在性校验。
 *
 * <ul>
 *   <li>归属：每个非底座 entity 表必须以某个 enabled 模块的 {@code code_} 为前缀。</li>
 *   <li>存在性：声明的 entity 表必须真实存在于当前 PostgreSQL schema。</li>
 *   <li>跳过：{@code sys_*} 前缀的底座表 + {@code wen-saas-boot.tenant.platform-only-tables} 白名单。</li>
 *   <li>DB 多余表（运维临时表、辅助表等）一律不管。</li>
 * </ul>
 *
 * 错误一次性收集后统一抛 {@link BusinessModuleConflictException}。
 */
@Component
public class ModuleTableValidator {

    /** 底座保留前缀。命名空间约定：业务模块表必须 {@code {module}_*}，底座表用 {@code sys_*}。 */
    private static final String CORE_TABLE_PREFIX = "sys_";

    private final MyBatisEntityTableScanner scanner;
    private final JdbcTemplate jdbcTemplate;
    private final TenantProperties tenantProperties;

    public ModuleTableValidator(MyBatisEntityTableScanner scanner,
                                 JdbcTemplate jdbcTemplate,
                                 TenantProperties tenantProperties) {
        this.scanner = scanner;
        this.jdbcTemplate = jdbcTemplate;
        this.tenantProperties = tenantProperties;
    }

    public void validate(List<BusinessModuleDescriptor> enabledModules) {
        if (enabledModules.isEmpty()) {
            // 没有任何业务模块时，所有非平台表都被视为"DB 多余表"，按设计跳过
            return;
        }
        Map<String, Class<?>> declaredTables = scanner.scan();
        if (declaredTables.isEmpty()) {
            return;
        }

        Set<String> platformWhitelist = lowercased(tenantProperties.getPlatformOnlyTables());
        Set<String> realTables = lowercased(fetchRealTables());

        // 模块 code 按长度降序匹配，避免短 code 误吃长 code 的表（如 crm vs crm_v2）
        List<String> moduleCodes = enabledModules.stream()
                .map(d -> d.metadata().code().toLowerCase())
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .toList();

        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, Class<?>> entry : declaredTables.entrySet()) {
            String tableName = entry.getKey().toLowerCase();
            Class<?> entityClass = entry.getValue();
            if (platformWhitelist.contains(tableName) || tableName.startsWith(CORE_TABLE_PREFIX)) {
                continue;
            }
            String owner = matchModule(tableName, moduleCodes);
            if (owner == null) {
                errors.add("表 '" + tableName + "'（实体 " + entityClass.getName()
                        + "）未归属任何已注册业务模块；如属于底座，请加入 wen-saas-boot.tenant.platform-only-tables");
                continue;
            }
            if (!realTables.contains(tableName)) {
                errors.add("表 '" + tableName + "'（实体 " + entityClass.getName()
                        + "）在 SPI 扫描中声明但 DB 不存在；请检查 Flyway 迁移");
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessModuleConflictException(
                    "业务模块表归属/存在性校验失败，启动阻断:\n  - " + String.join("\n  - ", errors));
        }
    }

    private static String matchModule(String tableName, List<String> codes) {
        for (String code : codes) {
            if (tableName.startsWith(code + "_")) {
                return code;
            }
        }
        return null;
    }

    private List<String> fetchRealTables() {
        return jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname = current_schema()",
                String.class);
    }

    private static Set<String> lowercased(java.util.Collection<String> in) {
        return in.stream().filter(java.util.Objects::nonNull).map(String::toLowerCase).collect(Collectors.toCollection(HashSet::new));
    }
}
