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
import com.wensaas.core.impl.compliance.export.ExportApprovalRule;
import com.wensaas.core.impl.compliance.export.ExportApprovalRuleMapper;
import com.wensaas.core.impl.compliance.export.ExportPolicy;
import com.wensaas.core.impl.compliance.export.ExportPolicyMapper;
import com.wensaas.core.impl.compliance.export.ExportRequest;
import com.wensaas.core.impl.compliance.export.ExportRequestMapper;
import com.wensaas.core.impl.ops.OpsAuthWebFilter;
import com.wensaas.core.impl.permission.role.SysUserRole;
import com.wensaas.core.impl.permission.role.SysUserRoleMapper;
import com.wensaas.core.impl.tenant.web.TenantContextWebFilter;
import com.wensaas.core.web.auth.integration.WebTestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据隐私与合规 · 超量导出审批集成测试。
 *
 * <p>覆盖：成员发起申请 → 主管理员审批通过的端到端闭环；非审批成员访问全部申请 403；
 * 策略读写；运营跨租户审计可见。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(TenantComplianceIntegrationTest.TestFixtures.class)
class TenantComplianceIntegrationTest {

    private static final long ROLE_SUPER_ADMIN = 12100001L;
    private static final String PASSWORD = "Admin@2026!";
    private static final String OPS_USER = "compliance-it-ops";
    private static final String OWNER_USER = "compliance-it-owner";
    private static final String MEMBER_USER = "compliance-it-member";
    private static final String TENANT_PREFIX = "TENCOMPLIANCE_IT_";

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationFilter authenticationFilter;
    @Autowired private TenantContextWebFilter tenantContextWebFilter;
    @Autowired private OpsAuthWebFilter opsAuthWebFilter;
    @Autowired private TestFixtures fixtures;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(authenticationFilter, tenantContextWebFilter, opsAuthWebFilter)
                .build();
        fixtures.cleanup();
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanup();
    }

    @Test
    void submit_approve_fullLoop_andOpsAudit() throws Exception {
        String tenantCode = TENANT_PREFIX + "A";
        long tenant = fixtures.seedTenant(tenantCode);
        fixtures.seedTenantUser(OWNER_USER, tenant, true);
        long memberId = fixtures.seedTenantUser(MEMBER_USER, tenant, false);
        fixtures.seedOpsUser(OPS_USER, ROLE_SUPER_ADMIN);

        String memberToken = login(MEMBER_USER, tenantCode);

        // 成员发起「成员列表」导出（默认需审批）→ 转 PENDING，objectType 取声明显示名
        MvcResult submit = mvc.perform(post("/api/v1/base/compliance/export-requests")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "objectCode", "member",
                                "filterSummary", "近 90 天 · 全部状态",
                                "estimatedCount", 52000,
                                "reason", "季度合规审计"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestNo").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.objectType").value("成员列表"))
                .andReturn();
        String requestId = JSON.parseObject(submit.getResponse().getContentAsString()).getString("id");

        // 发起「数据字典」导出（默认无需审批）→ 409，提示直接导出
        mvc.perform(post("/api/v1/base/compliance/export-requests")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "objectCode", "dict",
                                "reason", "无需审批的导出"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPORT_NOT_REQUIRED"));

        // 非审批成员无权看本租户全部申请
        mvc.perform(get("/api/v1/base/compliance/export-requests")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        // 主管理员看到待审批，并批准
        String ownerToken = login(OWNER_USER, tenantCode);
        mvc.perform(get("/api/v1/base/compliance/export-requests?status=PENDING")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("1"));
        mvc.perform(post("/api/v1/base/compliance/export-requests/" + requestId + "/approve")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("comment", "同意，注意脱敏"))))
                .andExpect(status().isNoContent());

        // 详情转为已批准
        mvc.perform(get("/api/v1/base/compliance/export-requests/" + requestId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvalComment").value("同意，注意脱敏"));

        // 重复审批冲突 409
        mvc.perform(post("/api/v1/base/compliance/export-requests/" + requestId + "/reject")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("comment", "改主意"))))
                .andExpect(status().isConflict());

        // 运营跨租户审计可见该申请
        String opsToken = login(OPS_USER);
        mvc.perform(get("/api/v1/ops/compliance/export-requests?keyword=成员列表")
                        .header("Authorization", "Bearer " + opsToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].tenantName").value(tenantCode))
                .andExpect(jsonPath("$.items[0].status").value("APPROVED"));

        // 申请人本人也能看详情（非审批人路径）
        mvc.perform(get("/api/v1/base/compliance/export-requests/" + requestId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantId").value(String.valueOf(memberId)));
    }

    @Test
    void policy_update_and_read() throws Exception {
        String tenantCode = TENANT_PREFIX + "B";
        long tenant = fixtures.seedTenant(tenantCode);
        fixtures.seedTenantUser(OWNER_USER, tenant, true);

        String ownerToken = login(OWNER_USER, tenantCode);

        // 默认策略：各对象审批开关取声明默认值（member/audit_log 需审批、dict 不需），MAIN_ADMIN
        mvc.perform(get("/api/v1/base/compliance/policy")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalAuthority").value("MAIN_ADMIN"))
                .andExpect(jsonPath("$.objects[0].code").value("member"))
                .andExpect(jsonPath("$.objects[0].requiresApproval").value(true))
                .andExpect(jsonPath("$.objects[2].code").value("dict"))
                .andExpect(jsonPath("$.objects[2].requiresApproval").value(false));

        // 更新：把 dict 切为需审批 + 审批权限改为持权限角色 + 超时 24h
        mvc.perform(put("/api/v1/base/compliance/policy")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of(
                                "objects", List.of(Map.of("code", "dict", "requiresApproval", true)),
                                "approvalAuthority", "ROLE_PERMISSION",
                                "timeoutHours", 24))))
                .andExpect(status().isNoContent());

        // 读回：dict 覆盖为需审批，权限 / 超时已更新
        mvc.perform(get("/api/v1/base/compliance/policy")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[2].code").value("dict"))
                .andExpect(jsonPath("$.objects[2].requiresApproval").value(true))
                .andExpect(jsonPath("$.approvalAuthority").value("ROLE_PERMISSION"))
                .andExpect(jsonPath("$.timeoutHours").value(24));
    }

    private String login(String username) throws Exception {
        return login(username, null);
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
    @CrossTenantQuery("TenantComplianceIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysUserRoleMapper userRoleMapper;
        final SysTenantMapper tenantMapper;
        final ExportRequestMapper requestMapper;
        final ExportPolicyMapper policyMapper;
        final ExportApprovalRuleMapper ruleMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                     SysTenantMapper tenantMapper, ExportRequestMapper requestMapper,
                     ExportPolicyMapper policyMapper, ExportApprovalRuleMapper ruleMapper) {
            this.userMapper = userMapper;
            this.userRoleMapper = userRoleMapper;
            this.tenantMapper = tenantMapper;
            this.requestMapper = requestMapper;
            this.policyMapper = policyMapper;
            this.ruleMapper = ruleMapper;
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

        void seedOpsUser(String username, long roleId) {
            SysUser u = new SysUser();
            u.setTenantId(0L);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(PASSWORD));
            u.setRealName(username);
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);

            SysUserRole ur = new SysUserRole();
            ur.setTenantId(0L);
            ur.setUserId(u.getId());
            ur.setRoleId(roleId);
            ur.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(ur);
        }

        void cleanup() {
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
            List<Long> tenantIds = new ArrayList<>();
            for (SysTenant t : tenants) {
                tenantIds.add(t.getId());
            }
            if (!tenantIds.isEmpty()) {
                requestMapper.delete(Wrappers.<ExportRequest>lambdaQuery().in(ExportRequest::getTenantId, tenantIds));
                policyMapper.delete(Wrappers.<ExportPolicy>lambdaQuery().in(ExportPolicy::getTenantId, tenantIds));
                ruleMapper.delete(Wrappers.<ExportApprovalRule>lambdaQuery()
                        .in(ExportApprovalRule::getTenantId, tenantIds));
            }
            for (String username : List.of(OPS_USER, OWNER_USER, MEMBER_USER)) {
                SysUser u = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, username));
                if (u != null) {
                    userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                            .eq(SysUserRole::getUserId, u.getId()));
                    userMapper.deleteById(u.getId());
                }
            }
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
        }
    }
}
