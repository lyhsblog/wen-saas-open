package com.wensaas.core.api.ops;

import com.wensaas.core.api.auth.user.CapabilitiesService;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 运营租户管理服务。
 *
 * <p>所有方法绕过 MP TenantLine（运营操作不在任何租户上下文内），
 * 实现类标注 {@code @CrossTenantQuery}。
 */
public interface TenantOpsService {

    /** 开通新租户（事务性，10 步）。 */
    CreatedTenantResult createTenant(CreateTenantCommand cmd);

    /**
     * 分页查询租户列表（简要视图，含派生 activationStatus）。
     *
     * @param status             null=不过滤；0=暂停 / 1=正常 / 2=已到期
     * @param expiringWithinDays null=不过滤；整数=只返回 plans 中有任一包在 N 天内到期的租户
     * @param hasSeatAddon       null=不过滤；true=有加购；false=无加购
     */
    TenantPage listTenants(int page, int pageSize, String keyword,
                           Integer status, Integer expiringWithinDays, Boolean hasSeatAddon,
                           List<String> packageCodes, String sort);

    /** 租户详情（基本 + 主管理员 + 套餐 + 加购席位）。 */
    TenantDetail getTenantDetail(Long tenantId);

    /** 为指定租户的主管理员重新签发密码重置 token（旧 token 自动作废）。 */
    IssuedResetLink reissueResetLink(Long tenantId);

    /** 解除指定成员的登录锁定（运营端操作）。 */
    void unlockLoginAttempt(long tenantId, long userId);

    /** 编辑租户名（含修改原因，写入审计）。 */
    void updateTenantName(long tenantId, UpdateTenantNameCommand cmd);

    /** 编辑运营备注。 */
    void updateTenantRemark(long tenantId, String remark);

    /** 勾选套餐包（push 新 TenantPlan 到 sys_tenant.plans）。 */
    void assignPlan(long tenantId, AssignPlanCommand cmd);

    /** 取消套餐包（设 revokedAt=now，数据保留）。 */
    void revokePlan(long tenantId, RevokePlanCommand cmd);

    /** 续费套餐包（延长 expiresAt）。 */
    void renewPlan(long tenantId, RenewPlanCommand cmd);

    /** 加购席位（insert sys_tenant_seat_addon）。 */
    void addSeatAddon(long tenantId, AddSeatAddonCommand cmd);

    /** 重新指定主管理员（existing 成员 或 new 新建）。 */
    TransferMainAdminResult transferMainAdmin(long tenantId, TransferMainAdminCommand cmd);

    /** Tab5 账单/变更历史（来自 sys_operation_log，按 target_tenant_id + action 白名单）。 */
    TenantEventPage listTenantEvents(long tenantId, int page, int pageSize);

    /** Tab7 运营操作日志（来自 sys_operation_log，按 target_tenant_id 全量，时间倒序）。 */
    TenantAuditLogPage listTenantAuditLogs(long tenantId, int page, int pageSize);

    /** 运营端成员分页查询（按 username/realName 关键字过滤；标注主管理员）。 */
    TenantMemberPage listTenantMembers(long tenantId, String keyword, int page, int pageSize);

    /**
     * 解密查看某租户成员（含主管理员）完整手机 / 邮箱（明文）。详情 / 列表默认脱敏，
     * 本方法是唯一返明文出口；控制层 {@code @RequirePermission("ops.tenant.contact.reveal")} + {@code @Auditable} 留痕。
     */
    ContactReveal revealMemberContact(long tenantId, long userId);

    /** 运营账号候选人查询（tenant_id=0 且 status=1，按 username/realName 关键字过滤）。供选客户经理用。 */
    List<OpsUserItem> listOpsUsers(String keyword);

    /** 租户使用情况：成员数 / 近 30 天活跃成员 / 近 30 天每日登录次数趋势。 */
    UsageStats getTenantUsage(long tenantId);

    /**
     * 更新租户状态（运营端操作）。
     *
     * @param tenantId 目标租户 id
     * @param newStatus 目标状态：0=暂停 / 1=正常 / 2=已过期
     * @param operatorUserId 操作人 id（审计用，可为 null）
     * @throws TenantOpsException {@code TENANT_NOT_FOUND} / {@code INVALID_STATUS}
     */
    void updateStatus(long tenantId, int newStatus, Long operatorUserId);

    // ===================== 命令 / 结果 records =====================

    record CreateTenantCommand(
            String tenantName,
            String tenantCode,
            String tenantRemark,
            String adminUsername,
            String adminRealName,
            String adminMobile,
            String adminEmail,
            Long csmUserId,
            Integer memberCap,
            Long storageCap,
            Instant expireAt,
            String timezone,
            String fullName,
            String uscc,
            String industry,
            String scale,
            String region,
            List<String> tags,
            int seatAddonQty,
            String seatAddonRemark,
            List<String> packageCodes
    ) {}

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class CreatedTenantResult {
        private Long tenantId;
        private String tenantCode;
        private String tenantNo;
        private String adminUsername;
        private String resetToken;
        private Instant resetExpiresAt;
    }

    /**
     * 派生 activationStatus：
     * <ul>
     *   <li>{@code "paused"}：sys_tenant.status=2</li>
     *   <li>{@code "pending_activation"}：sys_tenant.status=1 且主管理员 sys_user.status=0</li>
     *   <li>{@code "activated"}：sys_tenant.status=1 且主管理员 sys_user.status=1</li>
     *   <li>{@code "unknown"}：main_admin_user_id 为空（异常态）</li>
     * </ul>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TenantListItem {
        private String id;
        private String code;
        private String name;
        private String tenantNo;
        private String adminUsername;
        private String csmUserId;
        private String csmName;
        private Integer status;
        private String activationStatus;
        private Instant createdAt;
        private Instant lastActiveAt;
        private int planCount;
        private List<String> planCodes;
        private Instant nearestExpireAt;
        private int seatAddonTotal;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TenantPage {
        private List<TenantListItem> items;
        private long total;
        private int page;
        private int pageSize;
    }

    /**
     * 重发 reset link 结果。
     */
    record IssuedResetLink(String token, Instant expiresAt, String adminUsername) {}

    /**
     * 租户详情聚合（4 Tab v1：基本 + 主管理员 + 套餐 + 加购席位）。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TenantDetail {
        private TenantBasic tenant;
        private MainAdmin mainAdmin;
        private List<CapabilitiesService.PackageView> plans;
        private SeatAddonSummary seatAddons;
    }

    record TenantBasic(
            String id,
            String code,
            String name,
            String tenantNo,
            String remark,
            String csmUserId,
            String csmName,
            Integer memberCap,
            Long storageCap,
            Instant expireAt,
            String timezone,
            String fullName,
            String uscc,
            String industry,
            String scale,
            String region,
            List<String> tags,
            Integer status,
            String activationStatus,
            Instant createdAt,
            Instant lastActiveAt
    ) {}

    record MainAdmin(
            String userId,
            String username,
            String realName,
            String mobile,
            String email,
            Integer status
    ) {}

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class SeatAddonSummary {
        private int totalQuantity;
        private List<SeatAddonHistoryItem> history;
    }

    /** 解密后的完整联系方式（明文）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ContactReveal {
        private String mobile;
        private String email;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class SeatAddonHistoryItem {
        private String id;
        private int quantity;
        private String remark;
        private String operatorId;
        private String operatorName;
        private Instant createdAt;
    }

    record UpdateTenantNameCommand(String newName, String reason) {}

    record AssignPlanCommand(String packageCode, Integer ttlDays, String remark) {}

    record RevokePlanCommand(String packageCode, String reason, boolean notifyAdmin) {}

    record RenewPlanCommand(String packageCode, int ttlDays, String remark) {}

    record AddSeatAddonCommand(int quantity, String remark) {}

    record TransferMainAdminCommand(
            String mode,           // "existing" | "new"
            String userId,         // mode=existing
            String username,       // mode=new
            String realName,       // mode=new
            String mobile,         // mode=new
            String email,          // mode=new
            String oldAdminHandling // "demote_admin" | "demote_member" | "disable"
    ) {}

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TransferMainAdminResult {
        private String newAdminUserId;
        private String newAdminUsername;
        private String resetToken;
        private Instant resetExpiresAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TenantEventItem {
        private String id;
        private String action;
        private String objectType;
        private String objectTypeLabel;
        private String objectId;
        private String objectDesc;
        private String operatorId;
        private String operatorName;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TenantEventPage {
        private java.util.List<TenantEventItem> items;
        private long total;
        private int page;
        private int pageSize;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TenantAuditLogItem {
        private String id;
        private String action;
        private String objectType;
        private String objectTypeLabel;
        private String objectId;
        private String objectDesc;
        private String operatorId;
        private String operatorName;
        private String requestId;
        private Integer statusCode;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TenantAuditLogPage {
        private java.util.List<TenantAuditLogItem> items;
        private long total;
        private int page;
        private int pageSize;
    }

    /** 运营端成员项。{@code status}：0=停用 / 1=正常；{@code mainAdmin}=是否当前主管理员。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TenantMemberItem {
        private String userId;
        private String username;
        private String realName;
        private String mobile;
        private String email;
        private Integer status;
        private boolean mainAdmin;
        private Instant lastLoginAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TenantMemberPage {
        private List<TenantMemberItem> items;
        private long total;
        private int page;
        private int pageSize;
    }

    /** 运营账号候选人项（选客户经理用）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class OpsUserItem {
        private String userId;
        private String username;
        private String realName;
    }

    /** 租户使用情况指标。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class UsageStats {
        private long memberCount;
        private long activeMembers30d;
        private List<LoginTrendPoint> loginTrend;
    }

    /** 登录趋势单点。{@code date}=YYYY-MM-DD（北京时区）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class LoginTrendPoint {
        private String date;
        private long count;
    }
}
