package com.wensaas.core.impl.tenant.isolation;

import com.wensaas.core.api.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.impl.tenant.TenantProperties;

class TenantLineHandlerImplTest {

    private final TenantProperties properties = new TenantProperties();
    private final TenantLineHandlerImpl handler = new TenantLineHandlerImpl(properties);

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void getTenantId_returnsLongValueOfCurrentContext() {
        TenantContext.set(42L);
        Expression e = handler.getTenantId();
        assertThat(e).isInstanceOf(LongValue.class);
        assertThat(((LongValue) e).getValue()).isEqualTo(42L);
    }

    @Test
    void getTenantId_throwsWhenContextUnset() {
        assertThatThrownBy(handler::getTenantId)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ignoreTable_trueForPlatformOnlyTable() {
        assertThat(handler.ignoreTable("sys_tenant")).isTrue();
        assertThat(handler.ignoreTable("sys_business_module")).isTrue();
        assertThat(handler.ignoreTable("flyway_schema_history")).isTrue();
    }

    @Test
    void ignoreTable_caseInsensitive() {
        assertThat(handler.ignoreTable("SYS_TENANT")).isTrue();
        assertThat(handler.ignoreTable("Sys_Business_Module")).isTrue();
    }

    @Test
    void ignoreTable_falseForBusinessTable() {
        assertThat(handler.ignoreTable("crm_customer")).isFalse();
        assertThat(handler.ignoreTable("sys_user")).isFalse(); // 混合表，仍需注入
    }

    @Test
    void ignoreTable_nullSafe() {
        assertThat(handler.ignoreTable(null)).isFalse();
    }

    @Test
    void getTenantIdColumn_fixed() {
        assertThat(handler.getTenantIdColumn()).isEqualTo("tenant_id");
    }

    @Test
    void setEmptyPlatformOnlyTables_fallsBackToDefaults() {
        TenantProperties p = new TenantProperties();
        p.setPlatformOnlyTables(Set.of());
        assertThat(p.getPlatformOnlyTables()).contains("sys_tenant", "sys_business_module");
    }
}
