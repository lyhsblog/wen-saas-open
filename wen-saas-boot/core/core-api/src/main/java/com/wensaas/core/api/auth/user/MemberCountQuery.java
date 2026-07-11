package com.wensaas.core.api.auth.user;

/**
 * 成员计数读（auth domain 读服务，{@code UserQueryService} 之外按 ISP 单列的窄接口）。
 *
 * <p>租户成员统计，与「按 id 取单用户」语义不同，单列窄接口。供席位用量、健康分等场景。
 * 由 {@code UserQueryServiceImpl} 实现；消费方只注入本窄接口。
 */
public interface MemberCountQuery {

    /** 指定租户下启用（status=1）成员数。受 MP TenantLine 约束，沿用调用方租户上下文语义。 */
    long countActiveMembers(long tenantId);

    /** 指定租户下成员总数（不分状态）。沿用调用方租户上下文 / TenantLine 旁路语义（不另加旁路）。 */
    long countMembers(long tenantId);
}
