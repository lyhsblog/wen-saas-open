package com.wensaas.core.web.tenant.integration;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.tenant.SysTenantMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.ops.OpsAuthWebFilter;
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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户个人中心集成测试。
 *
 * <p>覆盖：基本信息 / 组织与角色 / 安全设置 三只读视图 + 改名 + 改密（无效 step-up 票据 401 /
 * 有效票据 204 后强制下线）+ 会话列表与「下线当前」409 + MFA 状态默认未开启。
 */
@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(TenantProfileIntegrationTest.TestFixtures.class)
class TenantProfileIntegrationTest {

    private static final String PASSWORD = "Admin@2026!";
    private static final String OWNER_USER = "tenant-profile-it-owner";
    private static final String TENANT_PREFIX = "TENPROFILE_IT_";

    @Autowired private WebApplicationContext context;
    @Autowired private AuthenticationFilter authenticationFilter;
    @Autowired private TenantContextWebFilter tenantContextWebFilter;
    @Autowired private OpsAuthWebFilter opsAuthWebFilter;
    @Autowired private TestFixtures fixtures;
    @Autowired private com.wensaas.core.impl.auth.stepup.StepUpTicketStore stepUpTicketStore;

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
    void readViews_and_updateProfile() throws Exception {
        String tenantCode = TENANT_PREFIX + "A";
        long tenant = fixtures.seedTenant(tenantCode);
        fixtures.seedOwner(OWNER_USER, tenant);
        String token = login(OWNER_USER, tenantCode);

        // 基本信息
        mvc.perform(get("/api/v1/base/profile/basic").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(OWNER_USER))
                .andExpect(jsonPath("$.mobile").value("13800001234"));

        // 组织与角色：主管理员 → 数据范围全部
        mvc.perform(get("/api/v1/base/profile/work").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value(true))
                .andExpect(jsonPath("$.dataScopeLabel").value("全部数据"))
                .andExpect(jsonPath("$.statusLabel").value("active"));

        // 安全设置：MFA 租户 opt-in，默认未开启、非强制
        mvc.perform(get("/api/v1/base/profile/security").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(jsonPath("$.mfaEnabled").value(false));

        // 禁自助改名（04a8722：姓名仅管理员可改）：PUT 携带 realName 被忽略，可自助项（性别）正常生效
        mvc.perform(put("/api/v1/base/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("realName", "新名字", "gender", 1))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/base/profile/basic").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realName").value(OWNER_USER))   // 姓名不可自助修改
                .andExpect(jsonPath("$.gender").value(1));             // 性别已更新
        // 注：/mfa 端点依赖通知模块（wen-saas-boot.notification.enabled），WebTestApp 未启用，故不在此断言；
        //     安全设置 tab 已覆盖 MFA 默认未开启（mfaServiceProvider 缺省时优雅降级）。
    }

    @Test
    void changePassword_and_sessions() throws Exception {
        String tenantCode = TENANT_PREFIX + "B";
        long tenant = fixtures.seedTenant(tenantCode);
        long userId = fixtures.seedOwner(OWNER_USER, tenant);
        String token = login(OWNER_USER, tenantCode);

        // 会话列表：当前会话被标记
        MvcResult sessions = mvc.perform(get("/api/v1/base/profile/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true))
                .andReturn();
        String sessionId = JSON.parseArray(sessions.getResponse().getContentAsString())
                .getJSONObject(0).getString("sessionId");

        // 下线当前会话 → 409
        mvc.perform(delete("/api/v1/base/profile/sessions/" + sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        // 改密：无效 step-up 票据 → 401
        mvc.perform(post("/api/v1/base/profile/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("stepUpToken", "bogus", "newPassword", "NewPass@2027!"))))
                .andExpect(status().isUnauthorized());

        // 改密：有效 step-up 票据（直接签发，等价于已通过任一通道验证）→ 204，旧 token 随即失效（全设备下线）
        String stepUpToken = stepUpTicketStore.issue(userId);
        mvc.perform(post("/api/v1/base/profile/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("stepUpToken", stepUpToken, "newPassword", "NewPass@2027!"))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/base/profile/basic").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String login(String username, String tenantCode) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("username", username);
        body.put("password", PASSWORD);
        body.put("tenantCode", tenantCode);
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
    }

    @Component
    @CrossTenantQuery("TenantProfileIT: test fixture")
    static class TestFixtures {

        final SysUserMapper userMapper;
        final SysTenantMapper tenantMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper, SysTenantMapper tenantMapper) {
            this.userMapper = userMapper;
            this.tenantMapper = tenantMapper;
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

        long seedOwner(String username, long tenantId) {
            SysUser u = new SysUser();
            u.setTenantId(tenantId);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(PASSWORD));
            u.setRealName(username);
            u.setMobile("13800001234");
            u.setEmail(username + "@example.com");
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);
            SysTenant patch = new SysTenant();
            patch.setId(tenantId);
            patch.setMainAdminUserId(u.getId());
            tenantMapper.updateById(patch);
            return u.getId();
        }

        void cleanup() {
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
            for (SysTenant t : tenants) {
                userMapper.delete(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getTenantId, t.getId()));
            }
            tenantMapper.delete(Wrappers.<SysTenant>lambdaQuery()
                    .likeRight(SysTenant::getCode, TENANT_PREFIX));
        }
    }
}
