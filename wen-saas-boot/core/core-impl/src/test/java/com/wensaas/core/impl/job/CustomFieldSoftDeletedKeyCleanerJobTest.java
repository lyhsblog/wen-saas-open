package com.wensaas.core.impl.job;

import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomFieldSoftDeletedKeyCleanerJobTest {

    private JdbcTemplate jdbc;
    private CustomFieldObjectRegistry objectRegistry;
    private CustomFieldSoftDeletedKeyCleanerJob job;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        objectRegistry = new CustomFieldObjectRegistry();
        job = new CustomFieldSoftDeletedKeyCleanerJob(jdbc, objectRegistry);
    }

    @Test
    void execute_cleansJsonKey_andDeletesMetadata() {
        objectRegistry.register("__test__",
                new CustomFieldObjectDef("crm.customer", "crm_customer", "extra", null));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", 1L, "tenant_id", 9L, "object_code", "crm.customer", "field_code", "cf_legacy")
        ));

        job.execute();

        verify(jdbc).update(eq("UPDATE crm_customer SET extra = extra - ? WHERE tenant_id = ?"),
                eq("cf_legacy"), eq(9L));
        verify(jdbc).update(eq("DELETE FROM sys_custom_field_def WHERE id = ?"), eq(1L));
    }

    @Test
    void execute_unregisteredObject_skips() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", 2L, "tenant_id", 9L, "object_code", "unknown.obj", "field_code", "cf_x")
        ));
        job.execute();
        verify(jdbc, never()).update(anyString(), any(), any());
        verify(jdbc, never()).update(anyString(), anyLong());
    }

    @Test
    void execute_invalidIdentifier_skipsAndContinues() {
        objectRegistry.register("__test__",
                new CustomFieldObjectDef("ok.obj", "ok_table", "extra", null));
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("id", 3L, "tenant_id", 9L, "object_code", "ok.obj", "field_code", "cf-bad"),
                Map.of("id", 4L, "tenant_id", 9L, "object_code", "ok.obj", "field_code", "cf_good")
        ));
        job.execute();
        // 仅 cf_good 命中 UPDATE + DELETE
        verify(jdbc, times(1)).update(eq("UPDATE ok_table SET extra = extra - ? WHERE tenant_id = ?"),
                eq("cf_good"), eq(9L));
        verify(jdbc, times(1)).update(eq("DELETE FROM sys_custom_field_def WHERE id = ?"), eq(4L));
    }
}
