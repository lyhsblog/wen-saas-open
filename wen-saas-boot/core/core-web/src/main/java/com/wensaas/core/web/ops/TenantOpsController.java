package com.wensaas.core.web.ops;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ops.TenantOpsService;
import com.wensaas.core.impl.auth.impersonate.ImpersonateService;
import com.wensaas.core.web.common.PageQuery;
import com.wensaas.core.web.ops.dto.AddSeatAddonRequest;
import com.wensaas.core.web.ops.dto.AssignPlanRequest;
import com.wensaas.core.web.ops.dto.ImpersonateNonceView;
import com.wensaas.core.web.ops.dto.ImpersonateRequest;
import com.wensaas.core.web.ops.dto.CreateTenantRequest;
import com.wensaas.core.web.ops.dto.CreatedTenantView;
import com.wensaas.core.web.ops.dto.ReissueResetLinkView;
import com.wensaas.core.web.ops.dto.RenewPlanRequest;
import com.wensaas.core.web.ops.dto.RevokePlanRequest;
import com.wensaas.core.web.ops.dto.TenantAuditLogPageView;
import com.wensaas.core.web.ops.dto.TenantDetailView;
import com.wensaas.core.web.ops.dto.TenantEventPageView;
import com.wensaas.core.web.ops.dto.TenantListItemView;
import com.wensaas.core.web.ops.dto.TenantListResponse;
import com.wensaas.core.web.ops.dto.TenantMemberPageView;
import com.wensaas.core.web.ops.dto.TenantUsageView;
import com.wensaas.core.web.ops.dto.TransferMainAdminRequest;
import com.wensaas.core.web.ops.dto.TransferMainAdminView;
import com.wensaas.core.web.ops.dto.UpdateTenantNameRequest;
import com.wensaas.core.web.ops.dto.UpdateTenantRemarkRequest;
import com.wensaas.core.web.ops.dto.TenantSuspendRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 运营后台 · 租户管理。
 *
 * <p>v1 权限：JWT 鉴权（平台账号 tenant_id=0）；运营角色体系推迟到运营后台权限会话。
 */
@Tag(name = "ops-tenant", description = "运营后台 · 租户管理")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/tenants")
public class TenantOpsController {

    private final TenantOpsService tenantOpsService;
    private final ImpersonateService impersonateService;

    public TenantOpsController(TenantOpsService tenantOpsService, ImpersonateService impersonateService) {
        this.tenantOpsService = tenantOpsService;
        this.impersonateService = impersonateService;
    }

    @Operation(summary = "开通新租户",
            description = "事务性创建租户 + 主管理员 + 系统预置角色 + 加购席位 + 密码重置 token（48h 有效）")
    @RequirePermission("ops.tenant.create")
    @Auditable(module = "__core__", action = "开通租户", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#result.tenantId()", objectDescExpr = "#result.tenantCode()",
            targetTenantIdExpr = "T(java.lang.Long).parseLong(#result.tenantId())")
    @PostMapping
    public CreatedTenantView createTenant(@Valid @RequestBody CreateTenantRequest req) {
        TenantOpsService.CreateTenantCommand cmd = new TenantOpsService.CreateTenantCommand(
                req.tenantName(), req.tenantCode(), req.tenantRemark(),
                req.adminUsername(), req.adminRealName(),
                req.adminMobile(), req.adminEmail(),
                req.csmUserId(), req.memberCap(), req.storageCap(), req.expireAt(), req.timezone(),
                req.fullName(), req.uscc(), req.industry(), req.scale(), req.region(), req.tags(),
                req.seatAddonQty() != null ? req.seatAddonQty() : 0, req.seatAddonRemark(),
                req.packageCodes() != null ? req.packageCodes() : List.of()
        );
        TenantOpsService.CreatedTenantResult result = tenantOpsService.createTenant(cmd);

        String resetExpiresAt = formatBeijing(result.resetExpiresAt());

        return new CreatedTenantView(
                String.valueOf(result.tenantId()),
                result.tenantCode(),
                result.tenantNo(),
                result.adminUsername(),
                result.resetToken(),
                resetExpiresAt
        );
    }

    @Operation(summary = "分页查询租户列表",
            description = "按名称/标识/内部编号关键字过滤，支持 status/expiringWithinDays/hasSeatAddon/packageCode 筛选；"
                    + "sort=lastActiveAt:desc|createdAt:desc|nearestExpireAt:asc（默认最近活跃倒序）；含派生 activationStatus 字段")
    @RequirePermission("ops.tenant.list")
    @GetMapping
    public TenantListResponse listTenants(
            @Valid @ParameterObject PageQuery pageQuery,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer expiringWithinDays,
            @RequestParam(required = false) Boolean hasSeatAddon,
            @RequestParam(required = false) List<String> packageCode,
            @RequestParam(required = false) String sort) {
        TenantOpsService.TenantPage tenantPage = tenantOpsService.listTenants(
                pageQuery.page(), pageQuery.size(), keyword, status, expiringWithinDays, hasSeatAddon, packageCode, sort);
        List<TenantListItemView> items = tenantPage.items().stream().map(item -> new TenantListItemView(
                item.id(), item.code(), item.name(), item.tenantNo(), item.adminUsername(),
                item.csmUserId(), item.csmName(),
                item.status(), item.activationStatus(),
                formatBeijing(item.createdAt()), formatBeijing(item.lastActiveAt()),
                item.planCount(), item.planCodes(),
                formatBeijing(item.nearestExpireAt()), item.seatAddonTotal()
        )).toList();
        return new TenantListResponse(items, tenantPage.total(), tenantPage.page(), tenantPage.pageSize());
    }

    @Operation(summary = "导出租户列表",
            description = "按与列表相同的筛选条件导出 CSV（UTF-8 BOM，Excel 友好）；最多 10000 行")
    @RequirePermission("ops.tenant.list")
    @Auditable(module = "__core__", action = "导出", objectType = EntityTypes.SYS_TENANT,
            recordRead = true)
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportTenants(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer expiringWithinDays,
            @RequestParam(required = false) Boolean hasSeatAddon,
            @RequestParam(required = false) List<String> packageCode,
            @RequestParam(required = false) String sort) {
        TenantOpsService.TenantPage p = tenantOpsService.listTenants(
                1, 10000, keyword, status, expiringWithinDays, hasSeatAddon, packageCode, sort);
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("租户名,编码,内部编号,状态,激活状态,客户经理,套餐数,最近到期,最后活跃,加购席位,创建时间\n");
        for (TenantOpsService.TenantListItem it : p.items()) {
            sb.append(csv(it.name())).append(',')
                    .append(csv(it.code())).append(',')
                    .append(csv(it.tenantNo())).append(',')
                    .append(csv(statusText(it.status()))).append(',')
                    .append(csv(it.activationStatus())).append(',')
                    .append(csv(it.csmName())).append(',')
                    .append(it.planCount()).append(',')
                    .append(csv(formatBeijing(it.nearestExpireAt()))).append(',')
                    .append(csv(formatBeijing(it.lastActiveAt()))).append(',')
                    .append(it.seatAddonTotal()).append(',')
                    .append(csv(formatBeijing(it.createdAt()))).append('\n');
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tenants.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @Operation(summary = "租户详情",
            description = "4 Tab v1：基本 + 主管理员 + 套餐（与 /api/v1/me/capabilities 共结构）+ 加购席位")
    @RequirePermission("ops.tenant.list")
    @GetMapping("/{tenantId}")
    public TenantDetailView getTenantDetail(@PathVariable Long tenantId) {
        TenantOpsService.TenantDetail d = tenantOpsService.getTenantDetail(tenantId);

        TenantDetailView.TenantBasicView basic = new TenantDetailView.TenantBasicView(
                d.tenant().id(), d.tenant().code(), d.tenant().name(),
                d.tenant().tenantNo(), d.tenant().remark(),
                d.tenant().csmUserId(), d.tenant().csmName(),
                d.tenant().memberCap(), d.tenant().storageCap(),
                formatBeijing(d.tenant().expireAt()), d.tenant().timezone(),
                d.tenant().fullName(), d.tenant().uscc(), d.tenant().industry(),
                d.tenant().scale(), d.tenant().region(), d.tenant().tags(),
                d.tenant().status(), d.tenant().activationStatus(),
                formatBeijing(d.tenant().createdAt()), formatBeijing(d.tenant().lastActiveAt())
        );
        TenantDetailView.MainAdminView mainAdmin = d.mainAdmin() != null
                ? new TenantDetailView.MainAdminView(
                        d.mainAdmin().userId(), d.mainAdmin().username(),
                        d.mainAdmin().realName(), d.mainAdmin().mobile(),
                        d.mainAdmin().email(), d.mainAdmin().status())
                : null;
        List<TenantDetailView.PlanView> plans = d.plans().stream()
                .map(p -> new TenantDetailView.PlanView(p.code(), p.name(), p.expiresAt(), p.readonly()))
                .toList();
        TenantDetailView.SeatAddonView seatAddons = new TenantDetailView.SeatAddonView(
                d.seatAddons().totalQuantity(),
                d.seatAddons().history().stream()
                        .map(h -> new TenantDetailView.SeatAddonHistoryItemView(
                                h.id(), h.quantity(), h.remark(), h.operatorId(), h.operatorName(),
                                formatBeijing(h.createdAt())))
                        .toList()
        );
        return new TenantDetailView(basic, mainAdmin, plans, seatAddons);
    }

    @Operation(summary = "重新生成主管理员密码重置链接",
            description = "旧 token 立即作废（invalidated_at=now），新 token 48h 有效；purpose=reset_admin")
    @RequirePermission("ops.tenant.reset_password")
    @Auditable(module = "__core__", action = "重发密码链接", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", targetTenantIdExpr = "#tenantId")
    @PostMapping("/{tenantId}/password-reset-link")
    public ReissueResetLinkView reissueResetLink(@PathVariable Long tenantId) {
        TenantOpsService.IssuedResetLink issued = tenantOpsService.reissueResetLink(tenantId);
        return new ReissueResetLinkView(
                issued.token(),
                formatBeijing(issued.expiresAt()),
                issued.adminUsername()
        );
    }

    @Operation(summary = "租户使用情况",
            description = "成员数 / 近 30 天活跃成员数 / 近 30 天每日登录趋势")
    @RequirePermission("ops.tenant.list")
    @GetMapping("/{tenantId}/usage")
    public TenantUsageView getTenantUsage(@PathVariable Long tenantId) {
        TenantOpsService.UsageStats u = tenantOpsService.getTenantUsage(tenantId);
        List<TenantUsageView.LoginTrendPointView> trend = u.loginTrend().stream()
                .map(p -> new TenantUsageView.LoginTrendPointView(p.date(), p.count()))
                .toList();
        return new TenantUsageView(u.memberCount(), u.activeMembers30d(), trend);
    }

    @Operation(summary = "运营端成员列表",
            description = "按 username/realName 关键字过滤的分页成员查询；mainAdmin 标注当前主管理员")
    @RequirePermission("ops.tenant.list")
    @GetMapping("/{tenantId}/members")
    public TenantMemberPageView listTenantMembers(@PathVariable Long tenantId,
                                                  @Valid @ParameterObject PageQuery pageQuery,
                                                  @RequestParam(required = false) String keyword) {
        TenantOpsService.TenantMemberPage result =
                tenantOpsService.listTenantMembers(tenantId, keyword, pageQuery.page(), pageQuery.size());
        List<TenantMemberPageView.TenantMemberItemView> items = result.items().stream()
                .map(m -> new TenantMemberPageView.TenantMemberItemView(
                        m.userId(), m.username(), m.realName(), m.mobile(), m.email(),
                        m.status(), m.mainAdmin(), formatBeijing(m.lastLoginAt())))
                .toList();
        return new TenantMemberPageView(items, result.total(), result.page(), result.pageSize());
    }

    @Operation(summary = "解密查看成员完整联系方式",
            description = "详情/列表默认脱敏；本端点返明文手机/邮箱，需 ops.tenant.contact.reveal 权限并留审计")
    @RequirePermission("ops.tenant.contact.reveal")
    @Auditable(module = "__core__", action = "查看联系方式", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId")
    @GetMapping("/{tenantId}/members/{userId}/contact")
    public TenantOpsService.ContactReveal revealMemberContact(@PathVariable Long tenantId,
                                                              @PathVariable Long userId) {
        return tenantOpsService.revealMemberContact(tenantId, userId);
    }

    @Operation(summary = "解除成员登录锁定",
            description = "清除该成员的 Redis 失败计数，允许立即重新登录")
    @RequirePermission("ops.tenant.reset_password")
    @Auditable(module = "__core__", action = "解除登录锁定", objectType = EntityTypes.SYS_USER,
            objectIdExpr = "#userId", targetTenantIdExpr = "#tenantId")
    @DeleteMapping("/{tenantId}/members/{userId}/login-lock")
    public ResponseEntity<Void> unlockLoginAttempt(@PathVariable Long tenantId,
                                                    @PathVariable Long userId) {
        tenantOpsService.unlockLoginAttempt(tenantId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "暂停租户",
            description = "暂停后该租户成员立即无法登录、API Key 返回 403；reason 写入操作日志")
    @RequirePermission("ops.tenant.suspend")
    @Auditable(module = "__core__", action = "暂停租户", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", objectDescExpr = "#req?.reason()", targetTenantIdExpr = "#tenantId")
    @PostMapping("/{tenantId}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable Long tenantId,
                                        @Valid @RequestBody(required = false) TenantSuspendRequest req) {
        long operatorId = com.wensaas.core.api.auth.user.UserContext.require().userId();
        tenantOpsService.updateStatus(tenantId, 0, operatorId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "恢复租户", description = "恢复正常状态，成员可正常登录")
    @RequirePermission("ops.tenant.suspend")
    @Auditable(module = "__core__", action = "恢复租户", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", targetTenantIdExpr = "#tenantId")
    @PostMapping("/{tenantId}/resume")
    public ResponseEntity<Void> resume(@PathVariable Long tenantId) {
        long operatorId = com.wensaas.core.api.auth.user.UserContext.require().userId();
        tenantOpsService.updateStatus(tenantId, 1, operatorId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "编辑租户名", description = "修改原因必填，写入审计日志")
    @RequirePermission("ops.tenant.update")
    @Auditable(module = "__core__", action = "修改名称", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", targetTenantIdExpr = "#tenantId")
    @PatchMapping("/{tenantId}/name")
    public ResponseEntity<Void> updateTenantName(@PathVariable Long tenantId,
                                                  @Valid @RequestBody UpdateTenantNameRequest req) {
        tenantOpsService.updateTenantName(tenantId,
                new TenantOpsService.UpdateTenantNameCommand(req.newName(), req.reason()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "编辑运营备注")
    @RequirePermission("ops.tenant.update")
    @Auditable(module = "__core__", action = "编辑备注", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", targetTenantIdExpr = "#tenantId")
    @PatchMapping("/{tenantId}/remark")
    public ResponseEntity<Void> updateTenantRemark(@PathVariable Long tenantId,
                                                    @Valid @RequestBody UpdateTenantRemarkRequest req) {
        tenantOpsService.updateTenantRemark(tenantId, req.remark());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "勾选套餐包", description = "向租户授权指定套餐包，有效期按 ttlDays 计算")
    @RequirePermission("ops.tenant.update")
    @Auditable(module = "__core__", action = "勾选套餐", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", targetTenantIdExpr = "#tenantId")
    @PostMapping("/{tenantId}/plans")
    public ResponseEntity<Void> assignPlan(@PathVariable Long tenantId,
                                            @Valid @RequestBody AssignPlanRequest req) {
        tenantOpsService.assignPlan(tenantId,
                new TenantOpsService.AssignPlanCommand(req.packageCode(), req.ttlDays(), req.remark()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "取消套餐包", description = "设 revokedAt=now，数据保留；必填取消原因")
    @RequirePermission("ops.tenant.update")
    @Auditable(module = "__core__", action = "取消套餐", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", targetTenantIdExpr = "#tenantId")
    @DeleteMapping("/{tenantId}/plans/{packageCode}")
    public ResponseEntity<Void> revokePlan(@PathVariable Long tenantId,
                                            @PathVariable String packageCode,
                                            @Valid @RequestBody RevokePlanRequest req) {
        tenantOpsService.revokePlan(tenantId,
                new TenantOpsService.RevokePlanCommand(packageCode, req.reason(), req.notifyAdmin()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "续费套餐包", description = "延长 expiresAt；过期则从续费日起算新周期")
    @RequirePermission("ops.tenant.update")
    @Auditable(module = "__core__", action = "续费套餐", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", targetTenantIdExpr = "#tenantId")
    @PostMapping("/{tenantId}/plans/{packageCode}/renewals")
    public ResponseEntity<Void> renewPlan(@PathVariable Long tenantId,
                                           @PathVariable String packageCode,
                                           @Valid @RequestBody RenewPlanRequest req) {
        tenantOpsService.renewPlan(tenantId,
                new TenantOpsService.RenewPlanCommand(packageCode, req.ttlDays(), req.remark()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "加购席位", description = "insert sys_tenant_seat_addon；席位永久有效")
    @RequirePermission("ops.tenant.update")
    @Auditable(module = "__core__", action = "加购席位", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", targetTenantIdExpr = "#tenantId")
    @PostMapping("/{tenantId}/seat-addons")
    public ResponseEntity<Void> addSeatAddon(@PathVariable Long tenantId,
                                              @Valid @RequestBody AddSeatAddonRequest req) {
        tenantOpsService.addSeatAddon(tenantId,
                new TenantOpsService.AddSeatAddonCommand(req.quantity(), req.remark()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "重新指定主管理员",
            description = "支持从已有成员选或新建账号；旧主管理员按 oldAdminHandling 降级或停用")
    @RequirePermission("ops.tenant.update")
    @Auditable(module = "__core__", action = "更换主管理员", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", targetTenantIdExpr = "#tenantId")
    @PostMapping("/{tenantId}/main-admin")
    public TransferMainAdminView transferMainAdmin(@PathVariable Long tenantId,
                                                    @Valid @RequestBody TransferMainAdminRequest req) {
        TenantOpsService.TransferMainAdminResult result = tenantOpsService.transferMainAdmin(tenantId,
                new TenantOpsService.TransferMainAdminCommand(
                        req.mode(), req.userId(), req.username(), req.realName(),
                        req.mobile(), req.email(), req.oldAdminHandling()));
        return new TransferMainAdminView(
                result.newAdminUserId(),
                result.newAdminUsername(),
                result.resetToken(),
                formatBeijing(result.resetExpiresAt())
        );
    }

    @Operation(summary = "Tab5 账单/变更历史",
            description = "来自 sys_operation_log target_tenant_id + action 白名单过滤，时间倒序")
    @RequirePermission("ops.tenant.list")
    @GetMapping("/{tenantId}/events")
    public TenantEventPageView listTenantEvents(@PathVariable Long tenantId,
                                                 @Valid @ParameterObject PageQuery pageQuery) {
        TenantOpsService.TenantEventPage result = tenantOpsService.listTenantEvents(tenantId, pageQuery.page(), pageQuery.size());
        List<TenantEventPageView.TenantEventItemView> items = result.items().stream()
                .map(e -> new TenantEventPageView.TenantEventItemView(
                        e.id(), e.action(), e.objectType(), e.objectTypeLabel(), e.objectId(), e.objectDesc(),
                        e.operatorId(), e.operatorName(), formatBeijing(e.createdAt())))
                .toList();
        return new TenantEventPageView(items, result.total(), result.page(), result.pageSize());
    }

    @Operation(summary = "Tab7 运营操作日志", description = "来自 sys_operation_log，该租户全量操作记录，时间倒序")
    @RequirePermission("ops.tenant.list")
    @GetMapping("/{tenantId}/audit-logs")
    public TenantAuditLogPageView listTenantAuditLogs(@PathVariable Long tenantId,
                                                       @Valid @ParameterObject PageQuery pageQuery) {
        TenantOpsService.TenantAuditLogPage result = tenantOpsService.listTenantAuditLogs(tenantId, pageQuery.page(), pageQuery.size());
        List<TenantAuditLogPageView.TenantAuditLogItemView> items = result.items().stream()
                .map(e -> new TenantAuditLogPageView.TenantAuditLogItemView(
                        e.id(), e.action(), e.objectType(), e.objectTypeLabel(), e.objectId(), e.objectDesc(),
                        e.operatorId(), e.operatorName(), e.requestId(), e.statusCode(),
                        formatBeijing(e.createdAt())))
                .toList();
        return new TenantAuditLogPageView(items, result.total(), result.page(), result.pageSize());
    }

    @Operation(summary = "代登录该租户",
            description = "签发一次性 nonce（5min）；前端跳转到租户端后由租户端调 POST /api/auth/impersonate/redeem 兑换 token")
    @RequirePermission("ops.tenant.impersonate")
    // object_desc 由 service 内 AuditObjects.add 登记的对象友好名（租户名、成员名）派生，故不设 objectDescExpr
    @Auditable(module = "__core__", action = "代登录", objectType = EntityTypes.SYS_TENANT,
            objectIdExpr = "#tenantId", targetTenantIdExpr = "#tenantId")
    @PostMapping("/{tenantId}/impersonate")
    public ImpersonateNonceView impersonate(@PathVariable Long tenantId,
                                             @Valid @RequestBody ImpersonateRequest req) {
        ImpersonateService.NonceIssued issued = impersonateService.startImpersonation(tenantId, req.asUserId());
        return new ImpersonateNonceView(issued.redeemNonce(), issued.targetTenantCode(), issued.expiresInSec());
    }

    private static String formatBeijing(Instant t) {
        if (t == null) return null;
        return t.atOffset(ZoneOffset.of("+08:00")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** CSV 字段转义：含逗号/引号/换行时用双引号包裹并转义内部引号。 */
    private static String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String statusText(Integer s) {
        if (s == null) return "";
        return switch (s) {
            case 0 -> "暂停";
            case 1 -> "正常";
            case 2 -> "已过期";
            default -> String.valueOf(s);
        };
    }
}
