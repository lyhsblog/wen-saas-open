package com.wensaas.core.impl.ops;

import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.api.tenant.CrossTenantQuery;

import java.util.List;

/**
 * 运营租户管理服务门面：实现 {@link TenantOpsService} 对外契约，按「开通 / 套餐 / 成员 / 详情」
 * 委派给 4 个协作类（M18 God Class 拆分，2026-06-18）。本类不含业务逻辑，仅做转发。
 *
 * <p>所有数据库操作绕过 MP TenantLine（{@code @CrossTenantQuery}），因为运营操作时不存在
 * 当前租户上下文。门面与各协作类均标 {@code @CrossTenantQuery}；
 * {@link com.wensaas.core.impl.tenant.isolation.IgnoreStrategyScopes} 为栈式作用域，嵌套进入安全。
 * 事务（{@code @Transactional}）随各写方法落在对应协作类上，门面不再持有事务。
 */
@CrossTenantQuery("TenantOpsService: 运营操作，无租户上下文")
public class TenantOpsServiceImpl implements TenantOpsService {

    private final TenantProvisioningService provisioning;
    private final TenantPlanOpsService plan;
    private final TenantMemberOpsService member;
    private final TenantDetailOpsService detail;

    public TenantOpsServiceImpl(TenantProvisioningService provisioning,
                                TenantPlanOpsService plan,
                                TenantMemberOpsService member,
                                TenantDetailOpsService detail) {
        this.provisioning = provisioning;
        this.plan = plan;
        this.member = member;
        this.detail = detail;
    }

    // ===================== 开通 =====================

    @Override
    public CreatedTenantResult createTenant(CreateTenantCommand cmd) {
        return provisioning.createTenant(cmd);
    }

    @Override
    public IssuedResetLink reissueResetLink(Long tenantId) {
        return provisioning.reissueResetLink(tenantId);
    }

    @Override
    public List<OpsUserItem> listOpsUsers(String keyword) {
        return provisioning.listOpsUsers(keyword);
    }

    // ===================== 套餐 / 席位 =====================

    @Override
    public void assignPlan(long tenantId, AssignPlanCommand cmd) {
        plan.assignPlan(tenantId, cmd);
    }

    @Override
    public void revokePlan(long tenantId, RevokePlanCommand cmd) {
        plan.revokePlan(tenantId, cmd);
    }

    @Override
    public void renewPlan(long tenantId, RenewPlanCommand cmd) {
        plan.renewPlan(tenantId, cmd);
    }

    @Override
    public void addSeatAddon(long tenantId, AddSeatAddonCommand cmd) {
        plan.addSeatAddon(tenantId, cmd);
    }

    // ===================== 成员 / 主管理员 =====================

    @Override
    public void unlockLoginAttempt(long tenantId, long userId) {
        member.unlockLoginAttempt(tenantId, userId);
    }

    @Override
    public ContactReveal revealMemberContact(long tenantId, long userId) {
        return member.revealMemberContact(tenantId, userId);
    }

    @Override
    public TransferMainAdminResult transferMainAdmin(long tenantId, TransferMainAdminCommand cmd) {
        return member.transferMainAdmin(tenantId, cmd);
    }

    @Override
    public TenantMemberPage listTenantMembers(long tenantId, String keyword, int page, int pageSize) {
        return member.listTenantMembers(tenantId, keyword, page, pageSize);
    }

    // ===================== 详情 / 基本信息 =====================

    @Override
    public TenantPage listTenants(int page, int pageSize, String keyword,
                                   Integer status, Integer expiringWithinDays, Boolean hasSeatAddon,
                                   List<String> packageCodes, String sort) {
        return detail.listTenants(page, pageSize, keyword, status, expiringWithinDays, hasSeatAddon,
                packageCodes, sort);
    }

    @Override
    public TenantDetail getTenantDetail(Long tenantId) {
        return detail.getTenantDetail(tenantId);
    }

    @Override
    public void updateTenantName(long tenantId, UpdateTenantNameCommand cmd) {
        detail.updateTenantName(tenantId, cmd);
    }

    @Override
    public void updateTenantRemark(long tenantId, String remark) {
        detail.updateTenantRemark(tenantId, remark);
    }

    @Override
    public void updateStatus(long tenantId, int newStatus, Long operatorUserId) {
        detail.updateStatus(tenantId, newStatus, operatorUserId);
    }

    @Override
    public TenantEventPage listTenantEvents(long tenantId, int page, int pageSize) {
        return detail.listTenantEvents(tenantId, page, pageSize);
    }

    @Override
    public TenantAuditLogPage listTenantAuditLogs(long tenantId, int page, int pageSize) {
        return detail.listTenantAuditLogs(tenantId, page, pageSize);
    }

    @Override
    public UsageStats getTenantUsage(long tenantId) {
        return detail.getTenantUsage(tenantId);
    }
}
