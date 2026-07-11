package com.wensaas.core.impl.job.integration;

import com.wensaas.core.impl.job.MonthlyPartitionCreatorJob;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 月分区自动创建端到端测试（真 PostgreSQL）。
 *
 * <p>用一个未来的年月（{@link #FUTURE_YM}）创建新分区，结束时 {@code DROP TABLE} 干净复位。
 * 通过查询 {@code pg_inherits} 校验分区从属关系。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonthlyPartitionCreatorIntegrationTest {

    private static final YearMonth FUTURE_YM = YearMonth.of(2099, 12);
    private static final String OP_PARTITION = "sys_operation_log_p209912";
    private static final String LOGIN_PARTITION = "sys_login_log_p209912";

    @Autowired JdbcTemplate jdbc;

    private MonthlyPartitionCreatorJob job;

    @BeforeEach
    void setUp() {
        job = new MonthlyPartitionCreatorJob(jdbc);
        // 防止跑前已有遗留
        jdbc.execute("DROP TABLE IF EXISTS " + OP_PARTITION);
        jdbc.execute("DROP TABLE IF EXISTS " + LOGIN_PARTITION);
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TABLE IF EXISTS " + OP_PARTITION);
        jdbc.execute("DROP TABLE IF EXISTS " + LOGIN_PARTITION);
    }

    @Test
    void create_attachesPartition_visibleInPgInherits() {
        job.createPartition("sys_operation_log", FUTURE_YM);

        // pg_inherits 应能看到 op 父 → 子的关系
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.relname AS child FROM pg_inherits i "
                        + "JOIN pg_class p ON p.oid = i.inhparent "
                        + "JOIN pg_class c ON c.oid = i.inhrelid "
                        + "WHERE p.relname = 'sys_operation_log' AND c.relname = ?", OP_PARTITION);
        assertThat(rows).hasSize(1);
    }

    @Test
    void create_isIdempotent_secondCallNoError() {
        job.createPartition("sys_login_log", FUTURE_YM);
        // 二次执行应无错误（IF NOT EXISTS）
        job.createPartition("sys_login_log", FUTURE_YM);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.relname FROM pg_inherits i "
                        + "JOIN pg_class p ON p.oid = i.inhparent "
                        + "JOIN pg_class c ON c.oid = i.inhrelid "
                        + "WHERE p.relname = 'sys_login_log' AND c.relname = ?", LOGIN_PARTITION);
        assertThat(rows).hasSize(1);
    }
}
