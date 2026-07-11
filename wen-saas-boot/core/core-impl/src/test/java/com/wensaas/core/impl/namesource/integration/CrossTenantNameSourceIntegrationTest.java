package com.wensaas.core.impl.namesource.integration;

import com.wensaas.core.api.namesource.CrossTenantView;
import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.namesource.TenantScope;
import com.wensaas.core.api.namesource.TransField;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import com.wensaas.core.impl.namesource.TransFiller;
import com.wensaas.core.impl.tenant.isolation.IgnoreStrategyScopes;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨租户名称回填门禁测试（设为合并门禁）。
 *
 * <p>固化范式：同一个普通源 {@link EntityTypes#SYS_USER}，{@code @TransField(scope = CROSS)} 经
 * {@code TransFiller} 就地旁路租户线 → 运营端在<b>别租户</b>上下文能翻出成员名；{@code scope = FOLLOW}
 * （默认，带租户线）则被租户线过滤、查空。证明跨租户取向由调用方声明 + filler 就地承载，无需单独的
 * {@code .xt} 源；并覆盖「FOLLOW 查空 vs CROSS 命中」的隔离边界。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossTenantNameSourceIntegrationTest {

    private static final long TENANT_A = 9811L; // 成员所属租户
    private static final long TENANT_B = 9812L; // 运营端「当前上下文」假设的别租户
    private static final long USER_ID = 981101L;

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityNameResolver resolver;
    @Autowired TransFiller transFiller;

    @BeforeEach
    void setup() {
        cleanup();
        jdbc.update("INSERT INTO sys_tenant (id, code, name, status) VALUES (?, 't9811', '租户A', 1)", TENANT_A);
        jdbc.update("INSERT INTO sys_tenant (id, code, name, status) VALUES (?, 't9812', '租户B', 1)", TENANT_B);
        // 成员属租户 A（直插绕租户线）
        jdbc.update("INSERT INTO sys_user (id, tenant_id, username, password_hash, real_name, status, created_at, updated_at) "
                + "VALUES (?, ?, 'xtuser', 'x', '张三', 1, now(), now())", USER_ID, TENANT_A);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @AfterAll
    void teardown() {
        cleanup();
        TenantContext.clear();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_user WHERE id = ?", USER_ID);
        jdbc.update("DELETE FROM sys_tenant WHERE id IN (?, ?)", TENANT_A, TENANT_B);
    }

    @Test
    void crossTenantScope_bypassesTenantLine_whileFollowIsFiltered() {
        TenantContext.set(TENANT_B); // 运营端「当前」非成员所属租户

        // FOLLOW（无旁路）：租户线注入 tenant_id=B → 属 A 的成员查空
        assertThat(resolver.resolve(EntityTypes.SYS_USER, List.of(USER_ID))).isEmpty();

        // CROSS：在 IgnoreStrategyScopes 旁路租户线内解析 → 命中（与 TransFiller 对 CROSS 组的处理一致）
        try (var ignored = IgnoreStrategyScopes.enter(IgnoreStrategyScopes.TENANT_LINE)) {
            assertThat(resolver.resolve(EntityTypes.SYS_USER, List.of(USER_ID)))
                    .containsEntry(USER_ID, "张三");
        }
    }

    @Test
    void fill_withCrossScope_resolvesCrossTenant() {
        TenantContext.set(TENANT_B);

        CrossItem cross = new CrossItem(USER_ID);
        transFiller.fill(cross);
        assertThat(cross.userName).isEqualTo("张三");

        // FOLLOW（默认，带租户线）type 在别租户上下文翻不出 → 留 null
        PlainItem plain = new PlainItem(USER_ID);
        transFiller.fill(plain);
        assertThat(plain.userName).isNull();
    }

    /** 运营端 DTO（@CrossTenantView）：跨租户成员名走 scope = CROSS。 */
    @CrossTenantView
    static class CrossItem {
        Long uid;
        @TransField(type = EntityTypes.SYS_USER, ref = "uid", scope = TenantScope.CROSS)
        String userName;

        CrossItem(Long uid) {
            this.uid = uid;
        }
    }

    /** 对照：默认 FOLLOW（带租户线），在别租户上下文翻不出。 */
    static class PlainItem {
        Long uid;
        @TransField(type = EntityTypes.SYS_USER, ref = "uid")
        String userName;

        PlainItem(Long uid) {
            this.uid = uid;
        }
    }
}
