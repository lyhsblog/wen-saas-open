package com.wensaas.core.impl.auth.oauth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.api.auth.login.AuthenticationException;
import com.wensaas.core.api.auth.login.LoginCredentials;
import com.wensaas.core.api.auth.login.LoginIdentityProvider;
import com.wensaas.core.api.auth.oauth.OAuthBindingRequiredException;
import com.wensaas.core.api.auth.oauth.OAuthClient;
import com.wensaas.core.api.auth.oauth.OAuthExchangeException;
import com.wensaas.core.api.auth.oauth.OAuthUserInfo;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.login.IdentityResolver;
import com.wensaas.core.impl.auth.login.PasswordLoginIdentityProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth 登录 Provider。按 {@code provider} 路由到对应 {@link OAuthClient} 换取渠道身份：
 * 命中绑定 → 直登；未命中 → 抛 {@link OAuthBindingRequiredException} 由 AuthService 转入绑定流。
 *
 * <p>登录时尚无 TenantContext，需跨租户查 binding / sys_user，故类上标 {@link CrossTenantQuery}
 * （同 {@link PasswordLoginIdentityProvider}）。
 */
@CrossTenantQuery
public class OAuthLoginIdentityProvider
        implements LoginIdentityProvider<LoginCredentials.OAuthCredentials> {

    private final Map<String, OAuthClient> clients;
    private final SysUserOAuthBindingMapper bindingMapper;
    private final SysUserMapper userMapper;
    private final IdentityResolver identityResolver;
    private final OAuthStateStore stateStore;

    public OAuthLoginIdentityProvider(List<OAuthClient> clients,
                                      SysUserOAuthBindingMapper bindingMapper,
                                      SysUserMapper userMapper,
                                      IdentityResolver identityResolver,
                                      OAuthStateStore stateStore) {
        this.clients = new HashMap<>();
        for (OAuthClient c : clients) {
            this.clients.put(c.provider(), c);
        }
        this.bindingMapper = bindingMapper;
        this.userMapper = userMapper;
        this.identityResolver = identityResolver;
        this.stateStore = stateStore;
    }

    @Override
    public Class<LoginCredentials.OAuthCredentials> supports() {
        return LoginCredentials.OAuthCredentials.class;
    }

    @Override
    public AuthenticatedIdentity authenticate(LoginCredentials.OAuthCredentials c) {
        if (c.provider() == null || c.provider().isBlank()) {
            throw new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                    "缺少第三方登录渠道");
        }
        OAuthClient client = clients.get(c.provider());
        if (client == null) {
            throw new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                    "暂不支持的第三方登录渠道：" + c.provider());
        }

        // CSRF 防护：强制校验并一次性消费服务端签发的 state，再换 authCode。
        // state 缺失 / 失效 / 与签发渠道不符 → 视作伪造或会话过期，拒绝（且不消耗 authCode）。
        if (!stateStore.consume(c.provider(), c.state())) {
            throw new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                    "OAuth state 校验失败（缺失/失效/不匹配），疑似 CSRF 或会话过期，请重新发起登录");
        }

        OAuthUserInfo info;
        try {
            info = client.exchange(c.authCode());
        } catch (OAuthExchangeException e) {
            // 换码失败 → 401；method 以 oauth_ 开头时 AuthServiceImpl 会落 fail_reason=oauth_failed。
            throw new AuthenticationException(AuthenticationException.Code.BAD_CREDENTIALS,
                    "第三方授权失败", e);
        }

        SysUserOAuthBinding binding = bindingMapper.selectOne(
                new LambdaQueryWrapper<SysUserOAuthBinding>()
                        .eq(SysUserOAuthBinding::getProvider, c.provider())
                        .eq(SysUserOAuthBinding::getExternalId, info.externalId())
                        .isNull(SysUserOAuthBinding::getDeletedAt)
                        .last("LIMIT 1"));
        if (binding == null) {
            // 未绑定：进入绑定流（非失败），携带渠道身份给 AuthService 暂存。
            throw new OAuthBindingRequiredException(c.provider(), info.externalId(),
                    info.unionId(), info.nickname(), info.avatarUrl());
        }

        SysUser user = userMapper.selectById(binding.getUserId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new AuthenticationException(AuthenticationException.Code.DISABLED, "账号不可用");
        }
        return identityResolver.of(user);
    }
}
