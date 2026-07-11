package com.wensaas.core.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wensaas.core.api.auth.login.ImpersonateException;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.impl.auth.login.AuthService;
import com.wensaas.core.impl.auth.impersonate.ImpersonateService;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import com.wensaas.core.impl.auth.session.RefreshTokenService;
import com.wensaas.core.impl.auth.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * AuthController 单元测试：覆盖代登录 redeem + exit-impersonation 两端点。
 * 已有登录 / 登出 / me 路径由 AuthEndToEndIntegrationTest 覆盖。
 */
class AuthControllerTest {

    private ImpersonateService impersonateService;
    private MockMvc mockMvc;
    private ObjectMapper json;

    @BeforeEach
    void setup() {
        impersonateService = mock(ImpersonateService.class);
        // AuthController 还需 refresh 相关依赖；这两个端点中它们仅做 cookie 写 / 吊销，用 mock 占位即可。
        AuthController controller = new AuthController(
                mock(AuthService.class), impersonateService,
                mock(RefreshCookie.class), mock(RefreshTokenService.class),
                mock(JwtTokenProvider.class),
                mock(SessionStore.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ImpersonateRestExceptionAdvice())
                .build();
        json = new ObjectMapper();
    }

    // ===== POST /api/auth/impersonate/redeem =====

    @Test
    void redeemImpersonate_happyPath_returnsToken() throws Exception {
        UserPrincipal principal = new UserPrincipal(200L, 100L, "tenantMember", "imp-sid", false, null, false, null);
        AuthService.LoginResult result = AuthService.LoginResult.completed("imp-token", Duration.ofHours(2), principal);
        when(impersonateService.redeemNonce("valid-nonce")).thenReturn(result);

        mockMvc.perform(post("/api/auth/impersonate/redeem")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nonce", "valid-nonce"))))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(200);
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"token\":\"imp-token\"");
                    assertThat(body).contains("\"expiresIn\":7200");
                    assertThat(body).contains("\"username\":\"tenantMember\"");
                });
    }

    @Test
    void redeemImpersonate_blankNonce_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/impersonate/redeem")
                        .contentType(APPLICATION_JSON)
                        .content("{\"nonce\":\"\"}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(400));
    }

    @Test
    void redeemImpersonate_nonceInvalid_returns404() throws Exception {
        when(impersonateService.redeemNonce(anyString()))
                .thenThrow(new ImpersonateException(
                        ImpersonateException.Code.IMPERSONATE_NONCE_INVALID, "nonce 无效或已过期"));

        mockMvc.perform(post("/api/auth/impersonate/redeem")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nonce", "expired-nonce"))))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(404);
                    assertThat(r.getResponse().getContentAsString())
                            .contains("IMPERSONATE_NONCE_INVALID");
                });
    }

    @Test
    void redeemImpersonate_operatorSessionGone_returns410() throws Exception {
        when(impersonateService.redeemNonce(anyString()))
                .thenThrow(new ImpersonateException(
                        ImpersonateException.Code.IMPERSONATE_OPERATOR_SESSION_GONE, "运营原会话已过期"));

        mockMvc.perform(post("/api/auth/impersonate/redeem")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nonce", "some-nonce"))))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(410);
                    assertThat(r.getResponse().getContentAsString())
                            .contains("IMPERSONATE_OPERATOR_SESSION_GONE");
                });
    }

    // ===== POST /api/auth/exit-impersonation =====
    // 注意：exit-impersonation 需要认证（非 @PublicEndpoint），
    // 但 MockMvcBuilders.standaloneSetup 不加 AuthenticationFilter，
    // 所以这里直接测 Controller 层逻辑（UserContext 未设置时抛 NPE/IllegalState）。
    // 完整认证流程由 ImpersonateIntegrationTest 覆盖。

    @Test
    void exitImpersonation_notImpersonating_returns400() throws Exception {
        doThrow(new ImpersonateException(
                ImpersonateException.Code.EXIT_IMPERSONATION_NOT_IMPERSONATING, "非代登录态"))
                .when(impersonateService).exitImpersonation(anyString());

        com.wensaas.core.api.auth.user.UserContext.set(
                new UserPrincipal(200L, 100L, "member", "imp-sid", false, null, false, null));
        try {
            mockMvc.perform(post("/api/auth/exit-impersonation"))
                    .andExpect(r -> {
                        assertThat(r.getResponse().getStatus()).isEqualTo(400);
                        assertThat(r.getResponse().getContentAsString())
                                .contains("EXIT_IMPERSONATION_NOT_IMPERSONATING");
                    });
        } finally {
            com.wensaas.core.api.auth.user.UserContext.clear();
        }
    }

    @Test
    void exitImpersonation_originalSessionGone_returns410() throws Exception {
        doThrow(new ImpersonateException(
                ImpersonateException.Code.EXIT_IMPERSONATION_ORIGINAL_GONE, "运营原会话已过期"))
                .when(impersonateService).exitImpersonation(anyString());

        com.wensaas.core.api.auth.user.UserContext.set(
                new UserPrincipal(200L, 100L, "member", "imp-sid", false, null, false, null));
        try {
            mockMvc.perform(post("/api/auth/exit-impersonation"))
                    .andExpect(r -> {
                        assertThat(r.getResponse().getStatus()).isEqualTo(410);
                        assertThat(r.getResponse().getContentAsString())
                                .contains("EXIT_IMPERSONATION_ORIGINAL_GONE");
                    });
        } finally {
            com.wensaas.core.api.auth.user.UserContext.clear();
        }
    }

    @Test
    void exitImpersonation_happyPath_returns204() throws Exception {
        com.wensaas.core.api.auth.user.UserContext.set(
                new UserPrincipal(200L, 100L, "member", "imp-sid", false, null, false, null));
        try {
            mockMvc.perform(post("/api/auth/exit-impersonation"))
                    .andExpect(r -> {
                        assertThat(r.getResponse().getStatus()).isEqualTo(204);
                        assertThat(r.getResponse().getContentAsString()).isEmpty();
                    });
            verify(impersonateService).exitImpersonation("imp-sid");
        } finally {
            com.wensaas.core.api.auth.user.UserContext.clear();
        }
    }
}
