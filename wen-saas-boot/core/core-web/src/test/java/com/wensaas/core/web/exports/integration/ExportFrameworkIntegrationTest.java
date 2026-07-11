package com.wensaas.core.web.exports.integration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
import com.wensaas.core.impl.compliance.export.ExportRequest;
import com.wensaas.core.impl.compliance.export.ExportRequestMapper;
import com.wensaas.core.impl.task.SysTask;
import com.wensaas.core.impl.task.SysTaskMapper;
import com.wensaas.core.impl.task.execution.TaskExecutionPublisher;
import com.wensaas.core.impl.task.execution.TaskRunner;
import com.wensaas.core.impl.tenant.web.TenantContextWebFilter;
import com.wensaas.core.web.auth.integration.WebTestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
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
 * 业务数据导出框架集成测试。
 *
 * <p>覆盖：①字典（默认无需审批）发起 → 直投任务 → handler 真实生成 xlsx 落文件中心 →
 * 任务下载口可换签名 URL；②成员（默认需审批）缺理由 422 → 带理由转 PENDING → 审批通过自动
 * 投递执行任务（creator = 申请人）→ 台账回写 task_id → 执行完成；③无权限成员发起 403；
 * ④可导出对象列表按权限过滤 + 审批开关标注。
 *
 * <p>任务执行：与 {@code DataExportIntegrationTest} 同惯例——noop {@link TaskExecutionPublisher}
 * 跳过 MQ，测试直调 {@link TaskRunner#run(long)}。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import({ExportFrameworkIntegrationTest.TestFixtures.class, ExportFrameworkIntegrationTest.NoMqConfig.class})
class ExportFrameworkIntegrationTest {

    private static final String PASSWORD = "Admin@2026!";
    private static final String OWNER_USER = "export-it-owner";
    private static final String MEMBER_USER = "export-it-member";
    private static final String TENANT_PREFIX = "TENEXPORT_IT_";

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationFilter authenticationFilter;
    @Autowired private TenantContextWebFilter tenantContextWebFilter;
    @Autowired private TestFixtures fixtures;
    @Autowired private TaskRunner taskRunner;

    private MockMvc mvc;

    /** 跳过真 MQ：noop publisher 让 submit 不投递，测试直调 TaskRunner.run 触发执行。 */
    @TestConfiguration
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
    void dictExport_noApproval_submitsTaskAndGeneratesXlsx() throws Exception {
        String tenantCode = TENANT_PREFIX + "A";
        long tenant = fixtures.seedTenant(tenantCode);
        long ownerId = fixtures.seedTenantUser(OWNER_USER, tenant, true);
        String ownerToken = login(OWNER_USER, tenantCode);

        // 可导出对象列表：主管理员三个全可见，dict 默认无需审批、member/audit_log 默认需审批
        MvcResult objectsRes = mvc.perform(get("/api/v1/exports/objects")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        List<JSONObject> objects = JSON.parseArray(
                objectsRes.getResponse().getContentAsString()).toJavaList(JSONObject.class);
        Assertions.assertEquals(3, objects.size(), "内置导出对象应为 member/audit_log/dict 三个");
        Assertions.assertFalse(findObject(objects, "dict").getBooleanValue("requiresApproval"));
        Assertions.assertTrue(findObject(objects, "member").getBooleanValue("requiresApproval"));
        Assertions.assertTrue(findObject(objects, "audit_log").getBooleanValue("requiresApproval"));

        // 字典导出：无需审批 → 直投任务
        MvcResult res = mvc.perform(post("/api/v1/exports")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "objectCode", "dict",
                                "filters", Map.of("includeDisabled", "true")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.taskId").exists())
                .andReturn();
        long taskId = JSON.parseObject(res.getResponse().getContentAsString()).getLongValue("taskId");

        runTask(tenant, ownerId, taskId);

        SysTask task = fixtures.task(taskId);
        Assertions.assertEquals("COMPLETED", task.getStatus());
        Assertions.assertEquals(ownerId, task.getCreatorId());
        Assertions.assertNotNull(task.getResultData());
        Assertions.assertNotNull(task.getResultData().get("fileId"), "结果应含导出文件 fileId");
        Assertions.assertEquals("EXPORT", task.getTaskType());

        // 任务中心统一下载口：302 → 签名 URL
        mvc.perform(get("/api/v1/tasks/" + taskId + "/download")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isFound());
    }

    @Test
    void memberExport_requiresApproval_thenApproveDispatchesAsApplicant() throws Exception {
        String tenantCode = TENANT_PREFIX + "B";
        long tenant = fixtures.seedTenant(tenantCode);
        long ownerId = fixtures.seedTenantUser(OWNER_USER, tenant, true);
        fixtures.seedTenantUser(MEMBER_USER, tenant, false);
        String ownerToken = login(OWNER_USER, tenantCode);

        // 普通成员无 org.member.list 权限 → 403
        String memberToken = login(MEMBER_USER, tenantCode);
        mvc.perform(post("/api/v1/exports")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("objectCode", "member", "reason", "试探"))))
                .andExpect(status().isForbidden());

        // 需审批对象缺申请理由 → 422
        mvc.perform(post("/api/v1/exports")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("objectCode", "member"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

        // 带理由发起 → 转审批 PENDING
        MvcResult res = mvc.perform(post("/api/v1/exports")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "objectCode", "member",
                                "filters", Map.of("status", "1"),
                                "filterSummary", "状态=启用",
                                "reason", "HR 月度盘点"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending_approval"))
                .andExpect(jsonPath("$.requestId").exists())
                .andReturn();
        long requestId = JSON.parseObject(res.getResponse().getContentAsString()).getLongValue("requestId");

        ExportRequest pending = fixtures.request(requestId);
        Assertions.assertEquals("PENDING", pending.getStatus());
        Assertions.assertNotNull(pending.getExecuteParams(), "申请应落执行参数快照");
        Assertions.assertNull(pending.getTaskId());

        // 审批通过 → 自动以申请人身份投递执行任务并回写 task_id
        mvc.perform(post("/api/v1/base/compliance/export-requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("comment", "同意"))))
                .andExpect(status().isNoContent());

        ExportRequest approved = fixtures.request(requestId);
        Assertions.assertEquals("APPROVED", approved.getStatus());
        Assertions.assertNotNull(approved.getTaskId(), "审批通过应投递任务并回写 task_id");

        SysTask task = fixtures.task(approved.getTaskId());
        Assertions.assertEquals(ownerId, task.getCreatorId(), "任务 creator 应为申请人（任务中心按 creator 隔离）");

        runTask(tenant, ownerId, approved.getTaskId());

        SysTask done = fixtures.task(approved.getTaskId());
        Assertions.assertEquals("COMPLETED", done.getStatus());
        Assertions.assertNotNull(done.getResultData().get("fileId"));
        // 成员导出按筛选（status=1）至少包含 owner + member 两行
        Assertions.assertTrue(Long.parseLong(String.valueOf(done.getResultData().get("rows"))) >= 2);
    }

    // ===================== 辅助 =====================

    private void runTask(long tenant, long operatorId, long taskId) {
        TenantContext.set(tenant);
        UserContext.set(new UserPrincipal(operatorId, tenant, "it-runner", "it-session",
                true, null, false, null));
        try {
            taskRunner.run(taskId);
        } finally {
            UserContext.clear();
            TenantContext.clear();
        }
    }

    private static JSONObject findObject(List<JSONObject> objects, String code) {
        return objects.stream().filter(o -> code.equals(o.getString("code"))).findFirst()
                .orElseThrow(() -> new AssertionError("缺少导出对象: " + code));
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
    @CrossTenantQuery("ExportFrameworkIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysTenantMapper tenantMapper;
        final ExportRequestMapper requestMapper;
        final SysTaskMapper taskMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysTenantMapper tenantMapper,
                     ExportRequestMapper requestMapper, SysTaskMapper taskMapper) {
            this.userMapper = userMapper;
            this.tenantMapper = tenantMapper;
            this.requestMapper = requestMapper;
            this.taskMapper = taskMapper;
        }

        ExportRequest request(long id) {
            return requestMapper.selectById(id);
        }

        SysTask task(long id) {
            return taskMapper.selectById(id);
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
                requestMapper.delete(Wrappers.<ExportRequest>lambdaQuery()
                        .in(ExportRequest::getTenantId, tenantIds));
                taskMapper.delete(Wrappers.<SysTask>lambdaQuery()
                        .in(SysTask::getTenantId, tenantIds));
            }
            for (String username : List.of(OWNER_USER, MEMBER_USER)) {
                List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, username));
                for (SysUser u : users) {
                    userMapper.deleteById(u.getId());
                }
            }
            for (SysTenant t : tenants) {
                tenantMapper.deleteById(t.getId());
            }
        }
    }
}
