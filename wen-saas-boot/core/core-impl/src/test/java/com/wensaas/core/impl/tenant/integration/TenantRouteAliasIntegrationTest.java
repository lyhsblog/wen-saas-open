package com.wensaas.core.impl.tenant.integration;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.tenant.selfservice.TenantCompanyException;
import com.wensaas.core.api.tenant.selfservice.TenantCompanyService;
import com.wensaas.core.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 路由位 90 天重定向别名（R6）集成测试：验证 {@code changeRouteSlot} 在真实 PG 下对
 * {@code sys_tenant_route_alias} 的写入 / upsert / 回收行为（Mockito 单测覆盖不到真 SQL）。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantRouteAliasIntegrationTest {

    private static final long TENANT_ID = 9821L;
    private static final long OTHER_TENANT_ID = 9822L;

    @Autowired JdbcTemplate jdbc;
    @Autowired TenantCompanyService companyService;

    @BeforeAll
    void setupClass() {
        cleanup();
    }

    @AfterAll
    void teardownClass() {
        cleanup();
        TenantContext.clear();
        UserContext.clear();
    }

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO sys_tenant (id, code, name, status) VALUES (?, ?, ?, 1)",
                TENANT_ID, "oldslug", "测试租户9821");
        TenantContext.set(TENANT_ID);
        UserContext.set(new UserPrincipal(700L, TENANT_ID, "admin", "sess", true, null, false, null));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        UserContext.clear();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_tenant_route_alias WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);
        jdbc.update("DELETE FROM sys_tenant WHERE id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);
    }

    @Test
    void changeRouteSlot_persistsAliasForOldCode() {
        companyService.changeRouteSlot("newslug");

        // 租户当前 code 已更新
        String code = jdbc.queryForObject("SELECT code FROM sys_tenant WHERE id = ?", String.class, TENANT_ID);
        assertThat(code).isEqualTo("newslug");

        // 旧 code 登记为别名，指向本租户，90 天后过期
        Map<String, Object> alias = jdbc.queryForMap(
                "SELECT tenant_id, old_code, expires_at FROM sys_tenant_route_alias WHERE old_code = ?", "oldslug");
        assertThat(((Number) alias.get("tenant_id")).longValue()).isEqualTo(TENANT_ID);
        LocalDateTime expiresAt = ((Timestamp) alias.get("expires_at")).toLocalDateTime();
        assertThat(expiresAt).isAfter(LocalDateTime.now().plusDays(89))
                .isBefore(LocalDateTime.now().plusDays(91));
    }

    @Test
    void changeRouteSlot_reclaimingAliasedCode_removesStaleAlias() {
        // 预置一条别名：某历史 code "reclaimme" 曾指向另一个租户
        jdbc.update("INSERT INTO sys_tenant_route_alias (id, tenant_id, old_code, expires_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                111111L, OTHER_TENANT_ID, "reclaimme",
                Timestamp.valueOf(LocalDateTime.now().plusDays(30)),
                Timestamp.valueOf(LocalDateTime.now()));

        // 本租户把路由位改成 "reclaimme"（无在用租户占用 → 允许）
        companyService.changeRouteSlot("reclaimme");

        // 陈旧别名被清掉（不再把 reclaimme 重定向到旧租户）
        Integer staleCount = jdbc.queryForObject(
                "SELECT count(*) FROM sys_tenant_route_alias WHERE old_code = ?", Integer.class, "reclaimme");
        assertThat(staleCount).isZero();

        // 同时本次变更把旧 code "oldslug" 登记为新别名
        Long aliasTenant = jdbc.queryForObject(
                "SELECT tenant_id FROM sys_tenant_route_alias WHERE old_code = ?", Long.class, "oldslug");
        assertThat(aliasTenant).isEqualTo(TENANT_ID);
    }

    @Test
    void changeRouteSlot_sameSlot_noAlias() {
        companyService.changeRouteSlot("oldslug"); // 幂等：未变化

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM sys_tenant_route_alias WHERE tenant_id = ?", Integer.class, TENANT_ID);
        assertThat(count).isZero();
    }

    @Test
    void changeRouteSlot_takenByAnotherTenant_throws() {
        jdbc.update("INSERT INTO sys_tenant (id, code, name, status) VALUES (?, ?, ?, 1)",
                OTHER_TENANT_ID, "occupied", "占位租户");

        assertThatThrownBy(() -> companyService.changeRouteSlot("occupied"))
                .isInstanceOf(TenantCompanyException.class)
                .extracting(e -> ((TenantCompanyException) e).code())
                .isEqualTo(TenantCompanyException.Code.ROUTE_SLOT_TAKEN);
    }
}
