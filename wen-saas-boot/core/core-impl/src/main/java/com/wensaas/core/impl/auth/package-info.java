/**
 * 认证与会话实现层。
 *
 * <ul>
 *   <li>{@link com.wensaas.core.impl.auth.AuthProperties}：配置</li>
 *   <li>{@link com.wensaas.core.impl.auth.session.JwtTokenProvider}：JWT 签发 / 解析</li>
 *   <li>{@link com.wensaas.core.impl.auth.session.SessionStore} + {@link com.wensaas.core.impl.auth.session.RedisSessionStore}
 *       ：Redis 会话存储</li>
 *   <li>{@link com.wensaas.core.impl.auth.password.PasswordHasher} + {@link com.wensaas.core.impl.auth.password.BCryptPasswordHasher}
 *       ：密码哈希</li>
 *   <li>{@link com.wensaas.core.impl.auth.login.AuthService} + {@link com.wensaas.core.impl.auth.login.AuthServiceImpl}
 *       ：登录 / 登出门面</li>
 *   <li>{@link com.wensaas.core.impl.auth.login.PasswordLoginIdentityProvider}：账号密码 Provider</li>
 *   <li>{@link com.wensaas.core.impl.auth.filter.AuthenticationFilter}：请求最前端过滤器</li>
 *   <li>{@link com.wensaas.core.impl.auth.filter.AuthPathMatcher}：白名单（配置 + {@code @PublicEndpoint}）</li>
 * </ul>
 */
package com.wensaas.core.impl.auth;
