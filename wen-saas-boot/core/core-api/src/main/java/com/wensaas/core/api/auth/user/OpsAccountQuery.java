package com.wensaas.core.api.auth.user;

import java.util.List;

/**
 * 运营账号查找（auth domain 读服务，{@code UserQueryService} 之外按 ISP 单列的窄接口）。
 *
 * <p>运营账号 = {@code tenant_id=0} 的启用用户。供「通知全体运营」「工单指派人下拉」等运营侧场景。
 * 由 {@code UserQueryServiceImpl} 实现；消费方只注入本窄接口。
 */
public interface OpsAccountQuery {

    /**
     * 全部启用（status=1）的运营账号 id（{@code tenant_id=0}）。
     *
     * <p>查询显式按平台租户过滤，但仍受 MP TenantLine 约束——由调用方保证处于平台上下文或
     * {@code @CrossTenantQuery} 范围内（与各消费方直查 Mapper 时一致，本服务不另加旁路）。
     */
    List<Long> activeOpsUserIds();

    /**
     * 全部启用（status=1）运营账号的（id + 展示名）。语义同 {@link #activeOpsUserIds()}，
     * 多一份展示名供「指派人」等下拉一次取齐，避免按 id 逐个查名。
     */
    List<OpsUserBrief> activeOpsUsers();

    /** 运营账号简档（id + 展示名 = realName）。 */
    record OpsUserBrief(Long id, String displayName) {}
}
