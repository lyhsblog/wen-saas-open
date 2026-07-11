package com.wensaas.core.web.auth.integration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.filter.AuthenticationFilter;
import com.wensaas.core.impl.auth.password.BCryptPasswordHasher;
import com.wensaas.core.impl.auth.login.LoginAttemptGuard;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.tenant.web.TenantContextWebFilter;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = WebTestApp.class)
@ActiveProfiles("dev")
@Import(AuthEndToEndIntegrationTest.TestFixtures.class)
class AuthEndToEndIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private AuthenticationFilter authenticationFilter;

    @Autowired
    private TenantContextWebFilter tenantContextWebFilter;

    @Autowired
    private LoginAttemptGuard loginAttemptGuard;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(authenticationFilter, tenantContextWebFilter)
                .build();
        fixtures.cleanUsers();
        fixtures.seedAdmin("admin", "admin123");
    }

    @AfterEach
    void tearDown() {
        fixtures.cleanUsers();
        // 清除登录失败计数，防止测试间互相影响
        loginAttemptGuard.unlock(0L, "admin");
    }

    @Test
    void login_success_returnsToken() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                // expiresIn 现为 access-token-ttl（短），不再是会话总时长；其值随配置变化，仅校验存在。
                .andExpect(jsonPath("$.expiresIn").exists())
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.tenantId").value(0))
                .andReturn();

        String token = JSON.parseObject(res.getResponse().getContentAsString()).getString("token");
        assertThat(token).isNotBlank();
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", "admin", "password", "wrong-pwd"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void login_unknownUsername_returns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", "ghost", "password", "x123456"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void login_disabledAccount_returns403() throws Exception {
        fixtures.disable("admin");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DISABLED"));
    }

    @Test
    void login_missingFields_returns422() throws Exception {
        // 框架 Bean 校验失败（@NotBlank）统一返 422+errors[]（72ec52a：前端表单字段级高亮依赖），不再是内置默认 400
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_TOKEN"));
    }

    @Test
    void protectedEndpoint_withBadToken_returns401() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void protectedEndpoint_withValidToken_returnsPrincipal() throws Exception {
        String token = login("admin", "admin123");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.tenantId").value(0));
    }

    @Test
    void logout_invalidatesSession() throws Exception {
        String token = login("admin", "admin123");

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NO_SESSION"));
    }

    @Test
    void healthEndpoint_isPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void login_unknownUsername_doesNotIncrementCounter() throws Exception {
        // 未知账号失败 5 次，已知账号不受影响
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.toJSONString(Map.of("username", "ghost", "password", "wrong!"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        }
        // admin 账号仍可正常登录
        login("admin", "admin123");
    }

    @Test
    void login_wrongPassword_thirdFailureReturnsAttemptsLeft() throws Exception {
        // 前 2 次失败不返回 attemptsLeft
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.toJSONString(Map.of("username", "admin", "password", "wrong!"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.attemptsLeft").doesNotExist());
        }
        // 第 3 次：count=3，remaining=2，attemptsLeft=2
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", "admin", "password", "wrong!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.attemptsLeft").value(2));
    }

    @Test
    void login_fiveWrongPasswords_returnsLocked() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.toJSONString(Map.of("username", "admin", "password", "wrong!"))))
                    .andExpect(status().isUnauthorized());
        }
        // 第 6 次：账号已锁定，423
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    @Test
    void login_afterSuccessCounterClears() throws Exception {
        // 4 次失败后，成功登录一次，计数清零
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(JSON.toJSONString(Map.of("username", "admin", "password", "wrong!"))))
                    .andExpect(status().isUnauthorized());
        }
        login("admin", "admin123");
        // 再失败一次（counter 已清零），不应锁定
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", "admin", "password", "wrong!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JSONObject body = JSON.parseObject(res.getResponse().getContentAsString());
        return body.getString("token");
    }

    /**
     * Seed / cleanup helper bean。整体加 {@code @CrossTenantQuery} 绕开 MP TenantLine
     * （测试没有 TenantContext，且 sys_user 是混合表）。
     */
    @Component
    @CrossTenantQuery
    static class TestFixtures {

        private final SysUserMapper userMapper;
        private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

        TestFixtures(SysUserMapper userMapper) {
            this.userMapper = userMapper;
        }

        void seedAdmin(String username, String password) {
            SysUser u = new SysUser();
            u.setTenantId(0L);
            u.setUsername(username);
            u.setPasswordHash(hasher.hash(password));
            u.setRealName("Test Admin");
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            u.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(u);
        }

        void disable(String username) {
            SysUser update = new SysUser();
            update.setStatus(0);
            userMapper.update(update, Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getUsername, username));
        }

        void cleanUsers() {
            // 物理删除，规避软删后再种导致 (tenant_id, username) 唯一冲突
            userMapper.delete(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getTenantId, 0L)
                    .in(SysUser::getUsername, "admin", "ghost"));
        }
    }
}
