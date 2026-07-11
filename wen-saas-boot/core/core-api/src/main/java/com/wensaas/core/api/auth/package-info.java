/**
 * 认证与会话契约层。
 *
 * <p>包含：
 * <ul>
 *   <li>{@link com.wensaas.core.api.auth.user.UserContext} / {@link com.wensaas.core.api.auth.user.UserPrincipal}
 *       ：当前请求的认证主体上下文（ThreadLocal）</li>
 *   <li>{@link com.wensaas.core.api.auth.permission.PublicEndpoint}：免认证接口标记</li>
 *   <li>{@link com.wensaas.core.api.auth.login.LoginIdentityProvider} + {@link com.wensaas.core.api.auth.login.LoginCredentials}
 *       ：登录方式扩展点（v1 仅 PasswordCredentials）</li>
 *   <li>{@link com.wensaas.core.api.auth.login.AuthenticationException}：统一异常</li>
 * </ul>
 *
 * <p>实现见 {@code com.wensaas.core.impl.auth}；Web 入口见 {@code com.wensaas.core.web.auth}。
 */
package com.wensaas.core.api.auth;
