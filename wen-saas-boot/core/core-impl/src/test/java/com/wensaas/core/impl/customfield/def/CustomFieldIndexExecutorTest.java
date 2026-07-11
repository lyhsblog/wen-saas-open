package com.wensaas.core.impl.customfield.def;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CustomFieldIndexExecutorTest {

    private JdbcTemplate jdbc;
    private CustomFieldIndexExecutor executor;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        executor = new CustomFieldIndexExecutor(jdbc);
    }

    @Test
    void createIndex_legalIdentifiers_emitsExpectedSql() {
        executor.createIndex("crm_customer", "extra", "customer_source");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).execute(sql.capture());
        assertThat(sql.getValue()).isEqualTo(
                "CREATE INDEX IF NOT EXISTS idx_crm_customer_extra_customer_source"
                        + " ON crm_customer (tenant_id, (extra->>'customer_source'))");
    }

    @Test
    void dropIndex_emitsExpectedSql() {
        executor.dropIndex("crm_customer", "customer_source");
        verify(jdbc).execute("DROP INDEX IF EXISTS idx_crm_customer_extra_customer_source");
    }

    @Test
    void createIndex_uppercaseTable_rejected() {
        assertThatThrownBy(() -> executor.createIndex("CRM_Customer", "extra", "src"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");
        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void createIndex_sqlInjection_inFieldCode_rejected() {
        assertThatThrownBy(() -> executor.createIndex(
                "crm_customer", "extra", "src'); DROP TABLE foo;--"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fieldCode");
        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void dropIndex_dashInTable_rejected() {
        assertThatThrownBy(() -> executor.dropIndex("crm-customer", "src"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void indexName_format() {
        assertThat(CustomFieldIndexExecutor.indexName("crm_customer", "src"))
                .isEqualTo("idx_crm_customer_extra_src");
    }
}
