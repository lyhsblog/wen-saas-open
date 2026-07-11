package com.wensaas.core.web.compliance.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.compliance.export.DataExportRequest;
import com.wensaas.core.impl.compliance.export.DataExportRequestMapper;
import com.wensaas.core.impl.task.execution.TaskExecutionPublisher;
import com.wensaas.core.impl.task.execution.TaskRunner;
import com.wensaas.core.impl.tenant.web.TenantContextWebFilter;
import com.wensaas.core.web.auth.integration.WebTestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据隐私与合规 · 客户全量数据导出集成测试。
 *
 * <p>覆盖：主管理员发起 → 落 PACKAGING；驱动任务中心 handler 打包完成（真实 zip 落文件中心 +
 * 下载令牌 + file_id + 7 天有效期）→ 详情完成态 + 下载端点换签名 URL；月限 1 次第二次发起 409；
 * 普通成员发起 403；列表可见。
 *
 * <p>打包任务执行：与 {@code TaskCenterIntegrationTest} 同惯例——注入 {@code @Primary} noop
 * {@link TaskExecutionPublisher} 跳过 MQ（dev broker.conf 遗留），
 * 测试直调 {@link TaskRunner#run(long)} 触发与生产消费者一致的执行链路，确定性、无 broker 依赖。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import({DataExportIntegrationTest.TestFixtures.class, DataExportIntegrationTest.NoMqConfig.class})
class DataExportIntegrationTest {

    private static final String PASSWORD = "Admin@2026!";
    private static final String OWNER_USER = "dataexport-it-owner";
    private static final String MEMBER_USER = "dataexport-it-member";
    private static final String TENANT_PREFIX = "TENDATAEXPORT_IT_";

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationFilter authenticationFilter;
    @Autowired private TenantContextWebFilter tenantContextWebFilter;
    @Autowired private TestFixtures fixtures;
    @Autowired private TaskRunner taskRunner;

    private MockMvc mvc;

    /** 跳过真 MQ：noop publisher 让 submit 不投递，测试直调 TaskRunner.run 触发打包。 */
    @org.springframework.boot.test.context.TestConfiguration
    static class NoMqConfig {
        @Bean
        @Primary
        TaskExecutionPublisher noopTaskExecutionPublisher() {
            return message -> { /* swallow：测试直调 TaskRunner.run，不经 MQ */ };
        }
    }

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
    void submit_packagesAsyncThenDownloadable_andEnforcesMonthlyLimit() throws Exception {
        String tenantCode = TENANT_PREFIX + "A";
        long tenant = fixtures.seedTenant(tenantCode);
        fixtures.seedTenantUser(OWNER_USER, tenant, true);
        fixtures.seedTenantUser(MEMBER_USER, tenant, false);

        // 普通成员无权发起
        String memberToken = login(MEMBER_USER, tenantCode);
        mvc.perform(post("/api/v1/base/compliance/data-exports")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("reason", "试探", "dataMode", "MASKED"))))
                .andExpect(status().isForbidden());

        // 主管理员发起 → 先落 PACKAGING，任务中心异步打包
        String ownerToken = login(OWNER_USER, tenantCode);
        MvcResult res = mvc.perform(post("/api/v1/base/compliance/data-exports")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("reason", "合规审计", "dataMode", "FULL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestNo").exists())
                .andExpect(jsonPath("$.status").value("PACKAGING"))
                .andExpect(jsonPath("$.dataMode").value("FULL"))
                .andReturn();
        String id = JSON.parseObject(res.getResponse().getContentAsString()).getString("id");

        // 驱动任务中心 handler 打包（与 TaskCenterIntegrationTest 同惯例：直调 TaskRunner.run 跳过 MQ）
        Long taskId = fixtures.taskId(Long.parseLong(id));
        org.junit.jupiter.api.Assertions.assertNotNull(taskId, "submit 应已投递打包任务并回写 taskId");
        TenantContext.set(tenant);
        UserContext.set(new UserPrincipal(900_000_001L, tenant, OWNER_USER, "it-session", true, null, false, null));
        try {
            taskRunner.run(taskId);
        } finally {
            UserContext.clear();
            TenantContext.clear();
        }

        DataExportRequest completed = fixtures.find(Long.parseLong(id));
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", completed.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(completed.getFileId());
        org.junit.jupiter.api.Assertions.assertNotNull(completed.getDownloadToken());
        org.junit.jupiter.api.Assertions.assertNotNull(completed.getExpiresAt());
        org.junit.jupiter.api.Assertions.assertTrue(completed.getFileSize() != null && completed.getFileSize() > 0);

        // 详情显示完成态 + 文件信息
        mvc.perform(get("/api/v1/base/compliance/data-exports/" + id)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.fileName").exists())
                .andExpect(jsonPath("$.scopeSummary").exists());

        // 下载端点换签名 URL
        mvc.perform(get("/api/v1/base/compliance/data-exports/" + id + "/download")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());

        // 月限 1 次：第二次发起 409
        mvc.perform(post("/api/v1/base/compliance/data-exports")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("reason", "再来一次", "dataMode", "MASKED"))))
                .andExpect(status().isConflict());

        // 列表可见
        mvc.perform(get("/api/v1/base/compliance/data-exports")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("1"))
                .andExpect(jsonPath("$.items[0].reason").value("合规审计"));
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
    @CrossTenantQuery("DataExportIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysTenantMapper tenantMapper;
        final DataExportRequestMapper requestMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysTenantMapper tenantMapper,
                     DataExportRequestMapper requestMapper) {
            this.userMapper = userMapper;
            this.tenantMapper = tenantMapper;
            this.requestMapper = requestMapper;
        }

        /** 读申请行（@CrossTenantQuery 故无需租户上下文）。 */
        DataExportRequest find(long id) {
            return requestMapper.selectById(id);
        }

        /** 读申请关联的打包任务 id（submit 投递任务后回写）。 */
        Long taskId(long id) {
            DataExportRequest r = requestMapper.selectById(id);
            return r == null ? null : r.getTaskId();
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

        void cleanup() {
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
            List<Long> tenantIds = new ArrayList<>();
            for (SysTenant t : tenants) {
                tenantIds.add(t.getId());
            }
            if (!tenantIds.isEmpty()) {
                requestMapper.delete(Wrappers.<DataExportRequest>lambdaQuery()
                        .in(DataExportRequest::getTenantId, tenantIds));
            }
            for (String username : List.of(OWNER_USER, MEMBER_USER)) {
                SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, username));
                if (u != null) {
                    userMapper.deleteById(u.getId());
                }
            }
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
        }
    }
}
