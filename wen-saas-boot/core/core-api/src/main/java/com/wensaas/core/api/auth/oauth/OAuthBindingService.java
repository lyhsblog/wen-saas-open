package com.wensaas.core.api.auth.oauth;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 第三方账号绑定查询与管理（租户成员自助）。已登录上下文调用，按 TenantContext 隔离。
 * 运营侧成员解绑走 {@code OpsTeamMemberAdminService.unbindOAuth}（运营成员，独立数据面）。
 */
public interface OAuthBindingService {

    /** 当前用户的活跃绑定列表。 */
    List<Binding> listByUser(long tenantId, long userId);

    /**
     * 已登录态主动绑定到当前账号：用 authCode 换渠道身份后写绑定关系。
     * 换码失败抛 {@link OAuthExchangeException}；渠道账号已被绑定抛 {@link OAuthBindConflictException}。
     */
    void bind(long tenantId, long userId, String provider, String authCode);

    /** 解绑（软删），幂等：无活跃绑定也正常返回。 */
    void unbind(long tenantId, long userId, String provider);

    record Binding(String provider, String nickname, String avatarUrl, LocalDateTime boundAt) {
    }
}
