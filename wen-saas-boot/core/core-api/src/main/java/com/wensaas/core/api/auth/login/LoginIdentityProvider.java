package com.wensaas.core.api.auth.login;

import com.wensaas.core.api.auth.user.AuthenticatedIdentity;
import com.wensaas.core.api.auth.user.UserPrincipal;
/**
 * 登录身份提供方。每种登录方式（账号密码、短信验证码、OAuth）实现一个 Bean，AuthService
 * 按 {@link LoginCredentials} 子类型路由。
 *
 * @param <C> 该 Provider 处理的凭证子类型
 */
public interface LoginIdentityProvider<C extends LoginCredentials> {

    /**
     * 该 Provider 处理的 credentials 类型。AuthService 按此匹配。
     */
    Class<C> supports();

    /**
     * 完成认证。失败必须抛 {@link AuthenticationException}，禁止返回 null。
     *
     * @return 已认证身份（不含 sessionId；由 AuthService 创建 Session 后升级为 {@link UserPrincipal}）
     */
    AuthenticatedIdentity authenticate(C credentials);
}
