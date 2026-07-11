package com.wensaas.core.impl.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MonthlyPartitionCreatorJobTest {

    private JdbcTemplate jdbc;
    private MonthlyPartitionCreatorJob job;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        job = new MonthlyPartitionCreatorJob(jdbc);
    }

    @Test
    void createPartition_correctSql() {
        job.createPartition("sys_operation_log", YearMonth.of(2026, 7));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).execute(sql.capture());
        assertThat(sql.getValue()).contains("sys_operation_log_p202607");
        assertThat(sql.getValue()).contains("FROM ('2026-07-01 00:00:00')");
        assertThat(sql.getValue()).contains("TO ('2026-08-01 00:00:00')");
        assertThat(sql.getValue()).contains("CREATE TABLE IF NOT EXISTS");
    }

    @Test
    void createPartition_yearBoundary() {
        // 12 月 → 跨年到下年 1 月
        job.createPartition("sys_login_log", YearMonth.of(2027, 1));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).execute(sql.capture());
        assertThat(sql.getValue()).contains("sys_login_log_p202701");
        assertThat(sql.getValue()).contains("FROM ('2027-01-01 00:00:00')");
        assertThat(sql.getValue()).contains("TO ('2027-02-01 00:00:00')");
    }

    @Test
    void createPartition_dataChangeLog() {
        job.createPartition("sys_data_change_log", YearMonth.of(2026, 10));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).execute(sql.capture());
        assertThat(sql.getValue()).contains("sys_data_change_log_p202610");
        assertThat(sql.getValue()).contains("FROM ('2026-10-01 00:00:00')");
        assertThat(sql.getValue()).contains("TO ('2026-11-01 00:00:00')");
    }

    @Test
    void createPartition_moduleAccessLog() {
        job.createPartition("sys_module_access_log", YearMonth.of(2026, 11));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).execute(sql.capture());
        assertThat(sql.getValue()).contains("sys_module_access_log_p202611");
        assertThat(sql.getValue()).contains("FROM ('2026-11-01 00:00:00')");
        assertThat(sql.getValue()).contains("TO ('2026-12-01 00:00:00')");
    }

    @Test
    void createPartition_unknownParent_throws() {
        assertThatThrownBy(() -> job.createPartition("sys_evil_log", YearMonth.of(2026, 9)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }
}
