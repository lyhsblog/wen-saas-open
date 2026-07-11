package com.wensaas.core.impl.auth;

import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.api.auth.login.AuthenticationException;
import com.wensaas.core.api.auth.login.LoginCredentials;
import com.wensaas.core.api.auth.login.LoginIdentityProvider;
import com.wensaas.core.api.auth.oauth.OAuthBindingRequiredException;
import com.wensaas.core.impl.auth.login.AuthService;
import com.wensaas.core.impl.auth.login.AuthServiceImpl;
import com.wensaas.core.impl.auth.login.PasswordLoginIdentityProvider;
import com.wensaas.core.impl.auth.mfa.MfaService;
import com.wensaas.core.impl.auth.oauth.OAuthBindPreAuth;
import com.wensaas.core.impl.auth.oauth.OAuthBindPreAuthStore;
import com.wensaas.core.impl.auth.oauth.SysUserOAuthBinding;
import com.wensaas.core.impl.auth.oauth.SysUserOAuthBindingMapper;
import com.wensaas.core.impl.auth.session.JwtTokenProvider;
import com.wensaas.core.impl.auth.session.SessionStore;
import com.wensaas.core.impl.auth.user.UserCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** AuthService 的 OAuth 路由 + 绑定流单测（未绑→bindingPending、completeOAuthBinding 各分支）。 */
class AuthServiceImplOAuthTest {

    private SessionStore sessionStore;
    private JwtTokenProvider jwtTokenProvider;
    private UserCommandService userCommandService;
    private AuditLogRecorder auditLogRecorder;
    private OAuthBindPreAuthStore oauthBindStore;
    private PasswordLoginIdentityProvider passwordProvider;
    private SysUserOAuthBindingMapper bindingMapper;
    private LoginIdentityProvider<LoginCredentials.OAuthCredentials> oauthProvider;
    private AuthServiceImpl authService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        sessionStore = mock(SessionStore.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userCommandService = mock(UserCommandService.class);
        auditLogRecorder = mock(AuditLogRecorder.class);
        ObjectProvider<MfaService> mfaServiceProvider = mock(ObjectProvider.class);
        when(mfaServiceProvider.getIfAvailable()).thenReturn(null);
        oauthBindStore = mock(OAuthBindPreAuthStore.class);
        passwordProvider = mock(PasswordLoginIdentityProvider.class);
        bindingMapper = mock(SysUserOAuthBindingMapper.class);
        oauthProvider = mock(LoginIdentityProvider.class);
        when(oauthProvider.supports()).thenReturn(LoginCredentials.OAuthCredentials.class);

        authService = new AuthServiceImpl(List.of(oauthProvider), sessionStore, jwtTokenProvider,
                userCommandService, auditLogRecorder, mfaServiceProvider, oauthBindStore, passwordProvider,
                bindingMapper, false);
    }

    @Test
    void login_oauthUnbound_returnsBindingPending() {
        when(oauthProvider.authenticate(any()))
                .thenThrow(new OAuthBindingRequiredException("dingtalk", "ext", "uni", "昵称", "ava"));
        when(oauthBindStore.create(any())).thenReturn("btoken");

        AuthService.LoginResult r = authService.login(
                new LoginCredentials.OAuthCredentials("dingtalk", "code", null));

        assertThat(r.bindingRequired()).isTrue();
        assertThat(r.bindToken()).isEqualTo("btoken");
        assertThat(r.bindProfile().provider()).isEqualTo("dingtalk");
        assertThat(r.bindProfile().nickname()).isEqualTo("昵称");
        verify(oauthBindStore).create(any());
        verifyNoInteractions(auditLogRecorder); // 引导绑定不是失败，不写登录日志
    }

    @Test
    void completeOAuthBinding_success_writesBindingAndIssuesToken() {
        when(oauthBindStore.find("btoken")).thenReturn(Optional.of(
                new OAuthBindPreAuth("dingtalk", "ext", "uni", "昵称", "ava", Instant.now())));
        AuthenticatedIdentity identity = new AuthenticatedIdentity(5L, 9L, "alice", false, null, false, null);
        when(passwordProvider.authenticate(any())).thenReturn(identity);
        when(sessionStore.createSession(identity)).thenReturn("sid");
        when(jwtTokenProvider.issue("sid")).thenReturn("jwt");
        when(jwtTokenProvider.tokenTtl()).thenReturn(Duration.ofHours(2));

        AuthService.LoginResult r = authService.completeOAuthBinding("btoken",
                new LoginCredentials.PasswordCredentials("alice", "pw", "t1"));

        assertThat(r.token()).isEqualTo("jwt");
        assertThat(r.principal().userId()).isEqualTo(5L);
        verify(bindingMapper).insert((SysUserOAuthBinding) any());
        verify(oauthBindStore).consume("btoken");
    }

    @Test
    void completeOAuthBinding_expiredToken_throws() {
        when(oauthBindStore.find("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.completeOAuthBinding("gone",
                new LoginCredentials.PasswordCredentials("a", "p", null)))
                .isInstanceOf(AuthenticationException.class);
        verify(bindingMapper, never()).insert((SysUserOAuthBinding) any());
    }

    @Test
    void completeOAuthBinding_badPassword_doesNotWriteBinding() {
        when(oauthBindStore.find("btoken")).thenReturn(Optional.of(
                new OAuthBindPreAuth("dingtalk", "ext", "uni", "nick", null, Instant.now())));
        when(passwordProvider.authenticate(any())).thenThrow(
                new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS, "bad"));

        assertThatThrownBy(() -> authService.completeOAuthBinding("btoken",
                new LoginCredentials.PasswordCredentials("a", "wrong", null)))
                .isInstanceOf(AuthenticationException.class);
        verify(bindingMapper, never()).insert((SysUserOAuthBinding) any());
        verify(oauthBindStore, never()).consume(any());
    }

    @Test
    void completeOAuthBinding_opsAccount_rejected() {
        when(oauthBindStore.find("btoken")).thenReturn(Optional.of(
                new OAuthBindPreAuth("dingtalk", "ext", "uni", "nick", null, Instant.now())));
        AuthenticatedIdentity ops = new AuthenticatedIdentity(1L, 0L, "admin", false, null, false, null);
        when(passwordProvider.authenticate(any())).thenReturn(ops);

        assertThatThrownBy(() -> authService.completeOAuthBinding("btoken",
                new LoginCredentials.PasswordCredentials("admin", "pw", null)))
                .isInstanceOf(AuthenticationException.class);
        verify(bindingMapper, never()).insert((SysUserOAuthBinding) any());
    }
}
