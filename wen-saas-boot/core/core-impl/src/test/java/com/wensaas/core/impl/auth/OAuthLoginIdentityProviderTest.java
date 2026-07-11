package com.wensaas.core.impl.auth;

import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.api.auth.login.AuthenticationException;
import com.wensaas.core.api.auth.login.LoginCredentials;
import com.wensaas.core.api.auth.oauth.OAuthBindingRequiredException;
import com.wensaas.core.api.auth.oauth.OAuthClient;
import com.wensaas.core.api.auth.oauth.OAuthExchangeException;
import com.wensaas.core.api.auth.oauth.OAuthUserInfo;
import com.wensaas.core.impl.auth.login.IdentityResolver;
import com.wensaas.core.impl.auth.oauth.OAuthLoginIdentityProvider;
import com.wensaas.core.impl.auth.oauth.OAuthStateStore;
import com.wensaas.core.impl.auth.oauth.SysUserOAuthBinding;
import com.wensaas.core.impl.auth.oauth.SysUserOAuthBindingMapper;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** OAuth 登录 Provider 单测：已绑直登 / 未绑引导 / 换码失败 / 停用 / 不支持渠道。 */
class OAuthLoginIdentityProviderTest {

    private OAuthClient client;
    private SysUserOAuthBindingMapper bindingMapper;
    private SysUserMapper userMapper;
    private IdentityResolver identityResolver;
    private OAuthStateStore stateStore;
    private OAuthLoginIdentityProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(OAuthClient.class);
        when(client.provider()).thenReturn("dingtalk");
        bindingMapper = mock(SysUserOAuthBindingMapper.class);
        userMapper = mock(SysUserMapper.class);
        identityResolver = mock(IdentityResolver.class);
        stateStore = mock(OAuthStateStore.class);
        // 默认放行有效 state，单独的失败用例再覆盖。
        when(stateStore.consume("dingtalk", "state-1")).thenReturn(true);
        provider = new OAuthLoginIdentityProvider(List.of(client), bindingMapper, userMapper,
                identityResolver, stateStore);
    }

    private LoginCredentials.OAuthCredentials creds() {
        return new LoginCredentials.OAuthCredentials("dingtalk", "code-1", "state-1");
    }

    @Test
    void boundUser_directLogin() {
        when(client.exchange("code-1")).thenReturn(new OAuthUserInfo("oid", "uid", "nick", null));
        SysUserOAuthBinding binding = new SysUserOAuthBinding();
        binding.setUserId(5L);
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        SysUser user = new SysUser();
        user.setId(5L);
        user.setTenantId(9L);
        user.setUsername("alice");
        user.setStatus(1);
        when(userMapper.selectById(5L)).thenReturn(user);
        AuthenticatedIdentity expected = new AuthenticatedIdentity(5L, 9L, "alice", false, null, false, null);
        when(identityResolver.of(user)).thenReturn(expected);

        assertThat(provider.authenticate(creds())).isSameAs(expected);
    }

    @Test
    void unbound_throwsBindingRequired() {
        when(client.exchange("code-1")).thenReturn(new OAuthUserInfo("oid", "uid", "钉钉用户", null));
        when(bindingMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> provider.authenticate(creds()))
                .isInstanceOf(OAuthBindingRequiredException.class)
                .satisfies(e -> {
                    OAuthBindingRequiredException ex = (OAuthBindingRequiredException) e;
                    assertThat(ex.getExternalId()).isEqualTo("uid");
                    assertThat(ex.getProvider()).isEqualTo("dingtalk");
                    assertThat(ex.getNickname()).isEqualTo("钉钉用户");
                });
    }

    @Test
    void exchangeFails_throwsBadCredentials() {
        when(client.exchange(any())).thenThrow(new OAuthExchangeException("boom"));
        assertThatThrownBy(() -> provider.authenticate(creds()))
                .isInstanceOf(AuthenticationException.class)
                .satisfies(e -> assertThat(((AuthenticationException) e).getCode())
                        .isEqualTo(AuthenticationException.Code.BAD_CREDENTIALS));
    }

    @Test
    void boundButDisabled_throwsDisabled() {
        when(client.exchange("code-1")).thenReturn(new OAuthUserInfo("oid", "uid", "nick", null));
        SysUserOAuthBinding binding = new SysUserOAuthBinding();
        binding.setUserId(5L);
        when(bindingMapper.selectOne(any())).thenReturn(binding);
        SysUser user = new SysUser();
        user.setId(5L);
        user.setStatus(0);
        when(userMapper.selectById(5L)).thenReturn(user);

        assertThatThrownBy(() -> provider.authenticate(creds()))
                .isInstanceOf(AuthenticationException.class)
                .satisfies(e -> assertThat(((AuthenticationException) e).getCode())
                        .isEqualTo(AuthenticationException.Code.DISABLED));
    }

    @Test
    void invalidState_throwsBadCredentials_andDoesNotExchange() {
        // state 校验失败（缺失/失效/不匹配）→ 拒绝，且不消耗 authCode（不调 exchange）。架构审查 M4。
        LoginCredentials.OAuthCredentials bad =
                new LoginCredentials.OAuthCredentials("dingtalk", "code-1", "forged");
        when(stateStore.consume("dingtalk", "forged")).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(bad))
                .isInstanceOf(AuthenticationException.class)
                .satisfies(e -> assertThat(((AuthenticationException) e).getCode())
                        .isEqualTo(AuthenticationException.Code.BAD_CREDENTIALS));
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).exchange(any());
    }

    @Test
    void unsupportedProvider_throwsBadCredentials() {
        LoginCredentials.OAuthCredentials wechat = new LoginCredentials.OAuthCredentials("wechat", "c", null);
        assertThatThrownBy(() -> provider.authenticate(wechat))
                .isInstanceOf(AuthenticationException.class);
    }
}
