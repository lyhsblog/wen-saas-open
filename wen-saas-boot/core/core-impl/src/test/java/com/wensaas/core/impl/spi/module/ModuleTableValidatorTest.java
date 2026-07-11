package com.wensaas.core.impl.spi.module;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
import com.wensaas.core.impl.tenant.TenantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModuleTableValidatorTest {

    private MyBatisEntityTableScanner scanner;
    private JdbcTemplate jdbcTemplate;
    private TenantProperties tenantProperties;
    private ModuleTableValidator validator;

    @BeforeEach
    void setUp() {
        scanner = mock(MyBatisEntityTableScanner.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        tenantProperties = new TenantProperties();
        validator = new ModuleTableValidator(scanner, jdbcTemplate, tenantProperties);
    }

    @Test
    void noEntitiesScanned_passes() {
        when(scanner.scan()).thenReturn(Map.of());
        assertThatCode(() -> validator.validate(List.of(module("crm")))).doesNotThrowAnyException();
    }

    @Test
    void platformTableInWhitelist_isSkipped() {
        // sys_user 在白名单不需要归属
        Set<String> whitelist = new LinkedHashSet<>(tenantProperties.getPlatformOnlyTables());
        whitelist.add("sys_user");
        tenantProperties.setPlatformOnlyTables(whitelist);

        when(scanner.scan()).thenReturn(table("sys_user", Object.class));
        stubRealTables("sys_user");

        assertThatCode(() -> validator.validate(List.of())).doesNotThrowAnyException();
    }

    @Test
    void coreSysPrefixedTable_isSkipped() {
        // sys_user 不在 platform-only-tables 白名单（它是混合表），但底座表统一 sys_ 前缀，跳过归属校验
        when(scanner.scan()).thenReturn(table("sys_user", FakeEntity.class));
        stubRealTables("sys_user");
        assertThatCode(() -> validator.validate(List.of())).doesNotThrowAnyException();
    }

    @Test
    void validBusinessTable_passes() {
        when(scanner.scan()).thenReturn(table("crm_customer", FakeEntity.class));
        stubRealTables("crm_customer");

        assertThatCode(() -> validator.validate(List.of(module("crm")))).doesNotThrowAnyException();
    }

    @Test
    void tableWithoutAnyOwner_fails() {
        when(scanner.scan()).thenReturn(table("xyz_orphan", FakeEntity.class));
        stubRealTables("xyz_orphan");

        assertThatThrownBy(() -> validator.validate(List.of(module("crm"))))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("未归属任何已注册业务模块")
                .hasMessageContaining("xyz_orphan");
    }

    @Test
    void declaredTableMissingInDb_fails() {
        when(scanner.scan()).thenReturn(table("crm_customer", FakeEntity.class));
        stubRealTables(/* empty */);

        assertThatThrownBy(() -> validator.validate(List.of(module("crm"))))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("DB 不存在")
                .hasMessageContaining("crm_customer");
    }

    @Test
    void multipleErrors_collectedTogether() {
        Map<String, Class<?>> declared = new LinkedHashMap<>();
        declared.put("xyz_orphan", FakeEntity.class);
        declared.put("crm_missing", FakeEntity.class);
        when(scanner.scan()).thenReturn(declared);
        stubRealTables(/* empty */);

        assertThatThrownBy(() -> validator.validate(List.of(module("crm"))))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("xyz_orphan")
                .hasMessageContaining("crm_missing");
    }

    private void stubRealTables(String... tables) {
        when(jdbcTemplate.queryForList(ArgumentMatchers.anyString(), ArgumentMatchers.eq(String.class)))
                .thenReturn(List.of(tables));
    }

    private static Map<String, Class<?>> table(String name, Class<?> entity) {
        Map<String, Class<?>> m = new LinkedHashMap<>();
        m.put(name, entity);
        return m;
    }

    private static BusinessModuleDescriptor module(String code) {
        return new BusinessModuleDescriptor() {
            @Override
            public ModuleMetadata metadata() {
                return ModuleMetadata.of(code, code, "1.0.0");
            }
        };
    }

    static class FakeEntity {
    }
}
