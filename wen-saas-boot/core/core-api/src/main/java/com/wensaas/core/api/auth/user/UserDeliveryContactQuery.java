package com.wensaas.core.api.auth.user;

import java.util.List;

/**
 * 投递用明文联系方式（auth domain 读服务，{@code UserQueryService} 之外按 ISP 单列的窄接口）。
 *
 * <p><b>本接口独立的理由不止整洁——它返回明文 mobile/email</b>：单列一个窄接口，让「明文仅供通知投递、
 * 禁止用于展示」的边界清晰可控（消费方限 notification）。由 {@code UserQueryServiceImpl} 实现。
 */
public interface UserDeliveryContactQuery {

    /**
     * 单用户联系方式（**明文，仅供通知投递**，不脱敏）；不存在返回 {@code null}，不分状态。
     * 受调用方租户上下文 / TenantLine 约束。方法名标明 {@code ForDelivery} 用途，禁止用于展示。
     */
    UserContact contactForDelivery(long userId);

    /**
     * 多用户启用（status=1）联系方式（**明文，仅供通知投递**）；按入参 {@code userIds} 顺序返回，
     * 过滤禁用 / 不存在者。受调用方租户上下文 / TenantLine 约束。
     */
    List<UserContact> activeContactsForDelivery(List<Long> userIds);

    /** 用户联系方式（明文，投递用途）。 */
    record UserContact(long userId, Long tenantId, String email, String mobile) {}
}
