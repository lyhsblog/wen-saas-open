/**
 * 开放 API 公共类型。
 *
 * <p>请求经 {@code AppKeyAuthenticationFilter} 鉴权后注入 {@link com.wensaas.core.api.auth.user.UserPrincipal}
 * （{@code byApiKey=true}）：功能权限按凭证创建者身份走与普通用户一致的 {@code @RequirePermission}
 * 检查；数据权限不做行级过滤（凭证定位是租户级系统对接，取租户内全量数据，等同 scope=ALL）。
 */
package com.wensaas.core.api.openapi;
