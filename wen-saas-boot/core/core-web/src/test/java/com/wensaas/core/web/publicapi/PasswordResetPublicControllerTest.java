package com.wensaas.core.web.publicapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.auth.password.PasswordResetPublicService;
import com.wensaas.core.impl.auth.password.PasswordResetTokenService;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.password.SysPasswordResetToken;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.api.tenant.TenantQueryService.TenantInfo;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import com.wensaas.core.web.publicapi.dto.SubmitResetPasswordRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PasswordResetPublicControllerTest {

    private PasswordResetTokenService resetTokenService;
    private TenantQueryService tenantQuery;
    private SysUserMapper userMapper;
    private UserCommandService userCommandService;
    private PasswordHasher passwordHasher;
    private SessionStore sessionStore;
    private MockMvc mockMvc;
    private ObjectMapper json;

    @BeforeEach
    void setup() {
        resetTokenService = mock(PasswordResetTokenService.class);
        tenantQuery = mock(TenantQueryService.class);
        userMapper = mock(SysUserMapper.class);
        userCommandService = mock(UserCommandService.class);
        passwordHasher = mock(PasswordHasher.class);
        sessionStore = mock(SessionStore.class);
        PasswordResetPublicService service = new PasswordResetPublicService(
                resetTokenService, tenantQuery, userMapper, userCommandService, passwordHasher, sessionStore);
        PasswordResetPublicController controller = new PasswordResetPublicController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new PasswordResetPublicRestExceptionAdvice())
                .build();
        json = new ObjectMapper();
    }

    @Test
    void checkToken_valid_returnsVALID() throws Exception {
        SysPasswordResetToken token = newToken("tok1", null, null);
        when(resetTokenService.findByToken("tok1")).thenReturn(token);
        when(tenantQuery.tenantById(anyLong())).thenReturn(tenantInfo("Demo Co"));

        mockMvc.perform(get("/api/v1/public/reset-password/by-token/tok1"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"status\":\"VALID\"");
                    assertThat(body).contains("usernameMask");
                    assertThat(body).doesNotContain("\"status\":\"EXPIRED\"");
                });
    }

    @Test
    void checkToken_expired_returnsEXPIRED() throws Exception {
        SysPasswordResetToken token = newToken("tok2", null, null);
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(resetTokenService.findByToken("tok2")).thenReturn(token);
        when(tenantQuery.tenantById(anyLong())).thenReturn(tenantInfo(null));

        mockMvc.perform(get("/api/v1/public/reset-password/by-token/tok2"))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"status\":\"EXPIRED\"");
                });
    }

    @Test
    void checkToken_notFound_returns404() throws Exception {
        when(resetTokenService.findByToken("unknown")).thenReturn(null);

        mockMvc.perform(get("/api/v1/public/reset-password/by-token/unknown"))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(404);
                    assertThat(r.getResponse().getContentAsString()).contains("TOKEN_NOT_FOUND");
                    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
                });
    }

    @Test
    void checkToken_used_returnsUSED() throws Exception {
        SysPasswordResetToken token = newToken("tok3", Instant.now().minus(1, ChronoUnit.HOURS), null);
        when(resetTokenService.findByToken("tok3")).thenReturn(token);
        when(tenantQuery.tenantById(anyLong())).thenReturn(tenantInfo(null));

        mockMvc.perform(get("/api/v1/public/reset-password/by-token/tok3"))
                .andExpect(r -> {
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("\"status\":\"USED\"");
                });
    }

    @Test
    void submitReset_happyPath_updatesPasswordAndActivatesUser() throws Exception {
        SysPasswordResetToken token = newToken("tok4", null, null);
        SysUser user = new SysUser();
        user.setId(100L);
        user.setUsername("adminuser");
        user.setStatus(0);
        when(resetTokenService.findByToken("tok4")).thenReturn(token);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordHasher.hash(anyString())).thenReturn("$2a$10$hashed");

        SubmitResetPasswordRequest req = new SubmitResetPasswordRequest("adminuser", "Password123");
        mockMvc.perform(post("/api/v1/public/reset-password/by-token/tok4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(200);
                    String body = r.getResponse().getContentAsString();
                    assertThat(body).contains("tenantCode");
                });
        verify(userCommandService).updateById(any(SysUser.class));
        verify(resetTokenService).markUsed(anyLong());
    }

    @Test
    void submitReset_wrongUsername_returns409() throws Exception {
        SysPasswordResetToken token = newToken("tok5", null, null);
        SysUser user = new SysUser();
        user.setId(200L);
        user.setUsername("correctuser");
        when(resetTokenService.findByToken("tok5")).thenReturn(token);
        when(userMapper.selectOne(any())).thenReturn(user);

        SubmitResetPasswordRequest req = new SubmitResetPasswordRequest("wronguser", "Password123");
        mockMvc.perform(post("/api/v1/public/reset-password/by-token/tok5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(409);
                    assertThat(r.getResponse().getContentAsString()).contains("USERNAME_MISMATCH");
                });
        verify(userCommandService, never()).updateById(any(SysUser.class));
    }

    @Test
    void submitReset_weakPassword_returns422() throws Exception {
        SysPasswordResetToken token = newToken("tok6", null, null);
        SysUser user = new SysUser();
        user.setId(300L);
        user.setUsername("adminuser");
        when(resetTokenService.findByToken("tok6")).thenReturn(token);
        when(userMapper.selectOne(any())).thenReturn(user);

        // Only letters, no digits
        SubmitResetPasswordRequest req = new SubmitResetPasswordRequest("adminuser", "onlyletters");
        mockMvc.perform(post("/api/v1/public/reset-password/by-token/tok6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(422);
                    assertThat(r.getResponse().getContentAsString()).contains("PASSWORD_TOO_WEAK");
                });
    }

    @Test
    void submitReset_expiredToken_returns410() throws Exception {
        SysPasswordResetToken token = newToken("tok7", null, null);
        token.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
        when(resetTokenService.findByToken("tok7")).thenReturn(token);

        SubmitResetPasswordRequest req = new SubmitResetPasswordRequest("adminuser", "Password123");
        mockMvc.perform(post("/api/v1/public/reset-password/by-token/tok7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(r -> {
                    assertThat(r.getResponse().getStatus()).isEqualTo(410);
                    assertThat(r.getResponse().getContentAsString()).contains("TOKEN_EXPIRED");
                });
    }

    private SysPasswordResetToken newToken(String tokenStr, Instant usedAt, Instant invalidatedAt) {
        SysPasswordResetToken t = new SysPasswordResetToken();
        t.setId(1L);
        t.setToken(tokenStr);
        t.setUserId(100L);
        t.setTenantId(9001L);
        t.setTenantCode("demo");
        t.setUsernameMask("a***er");
        t.setPurpose("init_admin");
        t.setExpiresAt(Instant.now().plus(48, ChronoUnit.HOURS));
        t.setUsedAt(usedAt);
        t.setInvalidatedAt(invalidatedAt);
        t.setCreatedAt(Instant.now());
        t.setCreatedBy(1L);
        return t;
    }

    private static TenantInfo tenantInfo(String name) {
        return new TenantInfo(9001L, "demo", name, 1, null, null, null, false, null);
    }
}
