package com.wensaas.core.impl.tenant.integration;

import com.wensaas.core.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.impl.tenant.isolation.TenantLineHandlerImpl;

/**
 * 端到端验证：MyBatis-Plus + TenantLineInnerInterceptor + TenantLineHandlerImpl
 * 在真实 PostgreSQL 上对 SQL 自动注入 tenant_id。
 *
 * <p>用 {@link JdbcTemplate} 在测试前建一张临时混合表 {@code sys_tenant_test}
 *（绕过 Flyway，避免污染 schema history）。
 *
 * <p>Redis / Auth 等配置由 {@code core-impl/src/test/resources/application.yml} 提供
 * （AuthConfiguration 通过 com.wensaas 包扫描装配，需要 StringRedisTemplate 等基础设施）。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantInterceptorIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TenantTestMapper tenantTestMapper;

    @Autowired
    SysTenantCountMapper sysTenantMapper;

    @Autowired
    CrossTenantQueryService crossTenantService;

    @BeforeAll
    void setupSchema() {
        jdbc.execute("DROP TABLE IF EXISTS sys_tenant_test");
        jdbc.execute(
                "CREATE TABLE sys_tenant_test ("
                        + " id BIGINT PRIMARY KEY,"
                        + " tenant_id BIGINT NOT NULL,"
                        + " name VARCHAR(64) NOT NULL)");
        jdbc.update("INSERT INTO sys_tenant_test (id, tenant_id, name) VALUES (?, ?, ?)", 1, 0, "platform");
        jdbc.update("INSERT INTO sys_tenant_test (id, tenant_id, name) VALUES (?, ?, ?)", 2, 1, "tenant-1-a");
        jdbc.update("INSERT INTO sys_tenant_test (id, tenant_id, name) VALUES (?, ?, ?)", 3, 1, "tenant-1-b");
        jdbc.update("INSERT INTO sys_tenant_test (id, tenant_id, name) VALUES (?, ?, ?)", 4, 2, "tenant-2");
    }

    @AfterAll
    void teardown() {
        jdbc.execute("DROP TABLE IF EXISTS sys_tenant_test");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void tenant_1_seesOnlyOwnRows() {
        TenantContext.set(1L);
        assertThat(tenantTestMapper.count()).isEqualTo(2);
        assertThat(tenantTestMapper.findAllIds()).containsExactly(2L, 3L);
    }

    @Test
    void tenant_2_seesOnlyOwnRows() {
        TenantContext.set(2L);
        assertThat(tenantTestMapper.findAllIds()).containsExactly(4L);
    }

    @Test
    void platform_tenant0_seesPlatformRowOnly() {
        TenantContext.set(TenantContext.PLATFORM_TENANT_ID);
        assertThat(tenantTestMapper.findAllIds()).containsExactly(1L);
    }

    @Test
    void unknownTenant_seesNothing() {
        TenantContext.set(999L);
        assertThat(tenantTestMapper.count()).isZero();
    }

    @Test
    void platformOnlyTable_skipsInjection() {
        // sys_tenant 本身没有 tenant_id 字段。
        // 如果白名单失效，拦截器会注入 WHERE tenant_id = ?，导致 PG 报 column 不存在。
        // 此查询能成功执行即证明 platform-only 跳过生效。
        TenantContext.set(0L);
        assertThat(sysTenantMapper.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void mybatisQueryWithoutTenantContext_throws() {
        // ThreadLocal 未设置，TenantLineHandlerImpl.getTenantId 抛 IllegalStateException
        // MyBatis 把它包装为 MyBatisSystemException → PersistenceException → IllegalStateException
        TenantContext.clear();
        assertThatThrownBy(() -> tenantTestMapper.count())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TenantContext");
    }

    @Test
    void crossTenantQuery_bypassesInjection() {
        TenantContext.set(0L); // 即便当前是平台运营，也应通过注解看到所有 4 行
        assertThat(crossTenantService.countAcrossAllTenants()).isEqualTo(4);
        assertThat(crossTenantService.findAllIdsAcrossTenants()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void crossTenantQuery_clearsIgnoreAfterReturn() {
        TenantContext.set(1L);
        // 调用一次 @CrossTenantQuery 方法
        crossTenantService.countAcrossAllTenants();
        // 之后正常调用应当仍然按 tenant_id 注入（即不应该残留 ignore 状态）
        assertThat(tenantTestMapper.count()).isEqualTo(2);
    }
}
