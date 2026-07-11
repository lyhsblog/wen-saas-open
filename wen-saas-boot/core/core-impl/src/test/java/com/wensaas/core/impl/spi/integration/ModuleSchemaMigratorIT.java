package com.wensaas.core.impl.spi.integration;

import com.wensaas.core.impl.spi.module.ModuleSchemaMigrator;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证"每模块独立 Flyway 版本线"机制（{@link ModuleSchemaMigrator}）：
 * 用一个仅测试用的伪模块 {@code itmod}（脚本在 {@code db/migration/itmod/}）跑独立迁移，
 * 断言其建表生效、history 表为 {@code flyway_history_itmod}，且重复执行幂等、无脚本模块跳过。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ModuleSchemaMigratorIT {

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.execute("DROP TABLE IF EXISTS itmod_probe");
        jdbc.execute("DROP TABLE IF EXISTS flyway_history_itmod");
    }

    @Test
    void migratesModuleIntoOwnHistoryTable() {
        ModuleSchemaMigrator migrator = new ModuleSchemaMigrator(dataSource);

        migrator.migrate(List.of("itmod"));

        // 模块表已建
        assertThat(tableExists("itmod_probe")).isTrue();
        // 独立 history 表，且记录了 V1.0.0
        assertThat(tableExists("flyway_history_itmod")).isTrue();
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_history_itmod WHERE version = '1.0.0' AND success", Integer.class);
        assertThat(applied).isEqualTo(1);
        // 没有污染底座 history
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE description LIKE '%itmod%'", Integer.class))
                .isZero();
    }

    @Test
    void isIdempotentOnSecondRun() {
        ModuleSchemaMigrator migrator = new ModuleSchemaMigrator(dataSource);
        migrator.migrate(List.of("itmod"));
        // 二次执行不应报错、不应重复建表
        migrator.migrate(List.of("itmod"));
        assertThat(tableExists("itmod_probe")).isTrue();
    }

    @Test
    void skipsModuleWithoutScripts() {
        ModuleSchemaMigrator migrator = new ModuleSchemaMigrator(dataSource);
        // 不存在迁移脚本的模块：不建 history 表、不报错
        migrator.migrate(List.of("nonexistent_mod"));
        assertThat(tableExists("flyway_history_nonexistent_mod")).isFalse();
    }

    private boolean tableExists(String table) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = ?", Integer.class, table);
        return n != null && n > 0;
    }
}
