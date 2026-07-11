package com.wensaas.core.web.compliance.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.tenant.web.TenantContextWebFilter;
import com.wensaas.core.web.auth.integration.WebTestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据隐私与合规 · 数据留存与软删除策略总览集成测试。
 *
 * <p>覆盖：声明式策略矩阵返回；当前租户文件回收站按删除时间分桶统计（总数 / 总字节 /
 * 即将清理 / 已超期），活跃文件不计入。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(RetentionIntegrationTest.TestFixtures.class)
class RetentionIntegrationTest {

    private static final String PASSWORD = "Admin@2026!";
    private static final String OWNER_USER = "retention-it-owner";
    private static final String TENANT_PREFIX = "TENRETENTION_IT_";

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationFilter authenticationFilter;
    @Autowired private TenantContextWebFilter tenantContextWebFilter;
    @Autowired private TestFixtures fixtures;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(authenticationFilter, tenantContextWebFilter)
                .build();
        fixtures.cleanup();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void retention_returnsPolicyMatrixAndRecycleBinStats() throws Exception {
        String tenantCode = TENANT_PREFIX + "A";
        long tenant = fixtures.seedTenant(tenantCode);
        fixtures.seedTenantUser(OWNER_USER, tenant, true);

        // 活跃文件（不计入回收站）
        fixtures.seedFile(tenant, null, 9999);
        // 回收站：删除 5 天前（既不即将清理也未超期）
        fixtures.seedFile(tenant, 5, 100);
        // 回收站：删除 25 天前（落在最后 7 天预警窗口 → 即将清理）
        fixtures.seedFile(tenant, 25, 200);
        // 回收站：删除 35 天前（已超 30 天 → 待下次清理任务彻底删除）
        fixtures.seedFile(tenant, 35, 400);

        String token = login(OWNER_USER, tenantCode);
        mvc.perform(get("/api/v1/base/compliance/retention")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies").isArray())
                .andExpect(jsonPath("$.policies[0].objectType").value("成员账号"))
                .andExpect(jsonPath("$.fileRetentionDays").value(30))
                .andExpect(jsonPath("$.purgeSchedule").exists())
                .andExpect(jsonPath("$.recycleBin.fileCount").value("3"))
                .andExpect(jsonPath("$.recycleBin.totalBytes").value("700"))
                .andExpect(jsonPath("$.recycleBin.expiringSoonCount").value("1"))
                .andExpect(jsonPath("$.recycleBin.overdueCount").value("1"))
                .andExpect(jsonPath("$.recycleBin.expiringWindowDays").value(7));
    }

    private String login(String username, String tenantCode) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("username", username);
        body.put("password", PASSWORD);
        if (tenantCode != null) {
            body.put("tenantCode", tenantCode);
        }
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    @Component
    @CrossTenantQuery("RetentionIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysTenantMapper tenantMapper;
        final JdbcTemplate jdbc;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysTenantMapper tenantMapper, JdbcTemplate jdbc) {
            this.userMapper = userMapper;
            this.tenantMapper = tenantMapper;
            this.jdbc = jdbc;
        }

        long seedTenant(String code) {
            SysTenant t = new SysTenant();
            t.setCode(code);
            t.setName(code);
            t.setStatus(1);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            tenantMapper.insert(t);
            return t.getId();
        }

        long seedTenantUser(String username, long tenantId, boolean asOwner) {
            SysUser u = new SysUser();
            u.setTenantId(tenantId);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(PASSWORD));
            u.setRealName(username);
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);
            if (asOwner) {
                SysTenant patch = new SysTenant();
                patch.setId(tenantId);
                patch.setMainAdminUserId(u.getId());
                tenantMapper.updateById(patch);
            }
            return u.getId();
        }

        /** 直插 sys_file（绕过 @TableLogic）；{@code deletedDaysAgo} 为 null 则活跃文件。 */
        void seedFile(long tenantId, Integer deletedDaysAgo, long sizeBytes) {
            long id = ThreadLocalRandom.current().nextLong(1L << 40, 1L << 62);
            LocalDateTime deletedAt = deletedDaysAgo == null
                    ? null : LocalDateTime.now().minusDays(deletedDaysAgo);
            jdbc.update("""
                    INSERT INTO sys_file
                      (id, tenant_id, file_id, file_name, size_bytes, visibility,
                       backend_type, backend_object_key, upload_state, deleted_at)
                    VALUES (?, ?, ?, ?, ?, 'private', 'local', ?, 'ready', ?)
                    """,
                    id, tenantId, "RETENTION_IT_" + id, "f-" + id + ".bin", sizeBytes,
                    "key/" + id, deletedAt);
        }

        void cleanup() {
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
            List<Long> tenantIds = new ArrayList<>();
            for (SysTenant t : tenants) {
                tenantIds.add(t.getId());
            }
            for (Long tid : tenantIds) {
                jdbc.update("DELETE FROM sys_file WHERE tenant_id = ?", tid);
            }
            SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, OWNER_USER));
            if (u != null) {
                userMapper.deleteById(u.getId());
            }
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
        }
    }
}
