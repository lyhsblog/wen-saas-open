package com.wensaas.core.impl.job;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 验证 v2 扩展：sys_api_call_log / sys_webhook_log 也能被分区 Job 创建。
 */
class MonthlyPartitionCreatorJobOpenApiTest {

    @Test
    void createsApiCallLogPartition() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MonthlyPartitionCreatorJob job = new MonthlyPartitionCreatorJob(jdbc);
        job.createPartition("sys_api_call_log", YearMonth.of(2026, 7));
        verify(jdbc).execute(anyString());
    }

    @Test
    void createsWebhookLogPartition() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MonthlyPartitionCreatorJob job = new MonthlyPartitionCreatorJob(jdbc);
        job.createPartition("sys_webhook_log", YearMonth.of(2026, 8));
        verify(jdbc).execute(anyString());
    }

    @Test
    void rejectsUnknownParentTable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MonthlyPartitionCreatorJob job = new MonthlyPartitionCreatorJob(jdbc);
        assertThatThrownBy(() -> job.createPartition("crm_lead_log", YearMonth.of(2026, 5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executeCovers6Parents() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MonthlyPartitionCreatorJob job = new MonthlyPartitionCreatorJob(jdbc);
        job.execute();
        // 6 张表都会触发 jdbc.execute 一次
        verify(jdbc, times(6)).execute(anyString());
    }
}
