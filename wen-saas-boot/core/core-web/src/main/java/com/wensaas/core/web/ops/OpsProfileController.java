package com.wensaas.core.web.ops;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.notification.NotificationPreferenceService;
import com.wensaas.core.api.ops.OpsLoginLogItem;
import com.wensaas.core.api.ops.OpsProfile;
import com.wensaas.core.api.ops.OpsProfileService;
import com.wensaas.core.api.ops.OpsSessionItem;
import com.wensaas.core.impl.auth.mfa.MfaService;
import com.wensaas.core.web.common.PageQuery;
import com.wensaas.core.web.common.PageResponse;
import com.wensaas.core.web.ops.dto.ChangePasswordRequest;
import com.wensaas.core.web.ops.dto.NotificationPreferenceView;
import com.wensaas.core.web.ops.dto.UpdateNotificationPreferencesRequest;
import com.wensaas.core.web.ops.dto.UpdateOpsProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
 * 运营后台 · 个人中心。
 *
 * <p>前缀 {@code /api/v1/ops/profile}，由 {@code OpsAuthWebFilter} 限定为运营账号（tenant_id=0）。
 * 当前主体取自 {@link UserContext}。
 */
@Tag(name = "ops-profile", description = "运营后台 · 个人中心")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/profile")
public class OpsProfileController {

    private final OpsProfileService opsProfileService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final ObjectProvider<MfaService> mfaServiceProvider;
    private final ObjectProvider<com.wensaas.core.impl.auth.stepup.StepUpService> stepUpServiceProvider;

    public OpsProfileController(OpsProfileService opsProfileService,
                                NotificationPreferenceService notificationPreferenceService,
                                ObjectProvider<MfaService> mfaServiceProvider,
                                ObjectProvider<com.wensaas.core.impl.auth.stepup.StepUpService> stepUpServiceProvider) {
        this.opsProfileService = opsProfileService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.mfaServiceProvider = mfaServiceProvider;
        this.stepUpServiceProvider = stepUpServiceProvider;
    }

    private MfaService mfaService() {
        MfaService svc = mfaServiceProvider.getIfAvailable();
        if (svc == null) {
            throw new IllegalStateException(
                    "MFA 端点需启用通知模块（wen-saas-boot.notification.enabled=true）");
        }
        return svc;
    }

    private com.wensaas.core.impl.auth.stepup.StepUpService stepUpService() {
        com.wensaas.core.impl.auth.stepup.StepUpService svc = stepUpServiceProvider.getIfAvailable();
        if (svc == null) {
            throw new IllegalStateException(
                    "敏感操作验证需启用通知模块（wen-saas-boot.notification.enabled=true）");
        }
        return svc;
    }

    @Operation(summary = "基本信息 tab", description = "个人中心「基本信息」tab 只读视图（前端切到该 tab 时拉取）")
    @GetMapping("/basic")
    public OpsProfile.Basic getBasic() {
        UserPrincipal p = UserContext.require();
        return opsProfileService.getBasic(p.tenantId(), p.userId());
    }

    @Operation(summary = "团队与角色 tab", description = "个人中心「团队与角色」tab 只读视图：平台角色 + 数据范围 + 账号状态")
    @GetMapping("/teams-roles")
    public OpsProfile.TeamsRoles getTeamsRoles() {
        UserPrincipal p = UserContext.require();
        return opsProfileService.getTeamsRoles(p.tenantId(), p.userId());
    }

    @Operation(summary = "安全设置 tab", description = "个人中心「安全设置」tab 只读视图：会话超时 / 锁定阈值 / MFA 状态")
    @GetMapping("/security")
    public OpsProfile.Security getSecurity() {
        UserPrincipal p = UserContext.require();
        return opsProfileService.getSecurity(p.tenantId(), p.userId());
    }

    @Operation(summary = "企业 SSO tab", description = "个人中心「企业 SSO」tab 只读视图（占位，当前无 SSO 系统）")
    @GetMapping("/sso")
    public OpsProfile.Sso getSso() {
        UserPrincipal p = UserContext.require();
        return opsProfileService.getSso(p.tenantId(), p.userId());
    }

    @Operation(summary = "修改基本信息", description = "可自助项：性别 / 生日 / 头像（姓名不可自助改，仅运营管理员在运营成员管理处变更）")
    @Auditable(module = "__core__", action = "编辑个人资料", objectType = EntityTypes.SYS_USER)
    @PutMapping
    public ResponseEntity<Void> updateProfile(@Valid @RequestBody UpdateOpsProfileRequest req) {
        UserPrincipal p = UserContext.require();
        opsProfileService.updateProfile(p.tenantId(), p.userId(),
                new OpsProfileService.ProfileUpdate(req.gender(), req.birthday(), req.avatarFileId()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "上传头像（≤1MB，JPG/PNG）", description = "落文件中心并回写 avatar_file_id，返回新 fileId + 签名 URL")
    @Auditable(module = "__core__", action = "上传头像", objectType = "")
    @PostMapping("/avatar")
    public OpsProfileService.AvatarResult uploadAvatar(@RequestParam("file") MultipartFile file)
            throws IOException {
        UserPrincipal p = UserContext.require();
        return opsProfileService.uploadAvatar(p.tenantId(), p.userId(),
                new OpsProfileService.AvatarUpload(
                        file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream()));
    }

    @Operation(summary = "修改密码", description = "step-up 验证身份 + 强度校验；成功后强制下线全部设备，需重新登录")
    @Auditable(module = "__core__", action = "修改密码", objectType = EntityTypes.SYS_USER)
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        UserPrincipal p = UserContext.require();
        stepUpService().consume(p.userId(), req.stepUpToken());
        opsProfileService.changePassword(p.tenantId(), p.userId(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "我的会话/设备", description = "列出当前用户全部活跃会话，标记当前会话")
    @GetMapping("/sessions")
    public List<OpsSessionItem> listSessions() {
        UserPrincipal p = UserContext.require();
        return opsProfileService.listSessions(p.tenantId(), p.userId(), p.sessionId());
    }

    @Operation(summary = "下线指定会话", description = "不能下线当前会话（409）；目标不属于本人返回 404")
    @Auditable(module = "__core__", action = "下线会话", objectType = EntityTypes.SYS_USER, objectIdExpr = "#sessionId")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        UserPrincipal p = UserContext.require();
        opsProfileService.revokeSession(p.tenantId(), p.userId(), p.sessionId(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "下线其他设备", description = "下线除当前会话外的全部会话")
    @Auditable(module = "__core__", action = "下线其他设备", objectType = EntityTypes.SYS_USER)
    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<Void> revokeOtherSessions() {
        UserPrincipal p = UserContext.require();
        opsProfileService.revokeOtherSessions(p.tenantId(), p.userId(), p.sessionId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "登录日志", description = "当前用户登录日志分页（created_at 倒序）")
    @GetMapping("/login-logs")
    public PageResponse<OpsLoginLogItem> listLoginLogs(@Valid @ParameterObject PageQuery pageQuery) {
        UserPrincipal p = UserContext.require();
        OpsProfileService.OpsLoginLogPage result = opsProfileService.listLoginLogs(
                p.tenantId(), p.userId(), pageQuery.page(), pageQuery.size());
        return new PageResponse<>(result.records(), result.total(), pageQuery.page(), pageQuery.size());
    }

    // ===== 安全验证：验证器绑定（独立于登录开关） + 登录二步验证开关 =====

    @Operation(summary = "绑定身份验证器 · 起手", description = "生成 TOTP 密钥 + otpauth URI（扫码/手动添加后凭动态码确认）")
    @Auditable(module = "__core__", action = "绑定身份验证器", objectType = EntityTypes.SYS_USER)
    @PostMapping("/mfa/totp/bind")
    public MfaService.SetupChallenge totpBindStart() {
        UserPrincipal p = UserContext.require();
        return mfaService().bindTotpStart(p.userId(), p.tenantId());
    }

    @Operation(summary = "绑定身份验证器 · 确认", description = "校验新验证器动态码后落库；重新绑定会覆盖旧密钥，不影响登录开关与恢复码")
    @Auditable(module = "__core__", action = "确认绑定身份验证器", objectType = EntityTypes.SYS_USER)
    @PostMapping("/mfa/totp/bind/confirm")
    public ResponseEntity<Void> totpBindConfirm(@Valid @RequestBody MfaCodeRequest req) {
        UserPrincipal p = UserContext.require();
        mfaService().bindTotpConfirm(p.userId(), p.tenantId(), req.code());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "开启登录二步验证 · 起手",
            description = "通道服务端推导：已绑验证器返回 TOTP（不发码）；否则向账号邮箱发送验证码")
    @Auditable(module = "__core__", action = "开启登录二步验证", objectType = EntityTypes.SYS_USER)
    @PostMapping("/mfa/enable")
    public MfaService.ChallengeInfo mfaEnableStart() {
        UserPrincipal p = UserContext.require();
        return mfaService().enableLoginMfaStart(p.userId(), p.tenantId());
    }

    @Operation(summary = "开启登录二步验证 · 确认", description = "按推导通道校验动态码 → 开启 + 生成 8 组一次性恢复码")
    @Auditable(module = "__core__", action = "确认开启登录二步验证", objectType = EntityTypes.SYS_USER)
    @PostMapping("/mfa/enable/confirm")
    public MfaRecoveryCodesResponse mfaEnableConfirm(@Valid @RequestBody MfaCodeRequest req) {
        UserPrincipal p = UserContext.require();
        List<String> codes = mfaService().enableLoginMfaConfirm(p.userId(), p.tenantId(), req.code());
        return new MfaRecoveryCodesResponse(codes);
    }

    @Operation(summary = "关闭登录二步验证", description = "需先经身份验证（stepUpToken）；关闭后清恢复码、保留验证器绑定")
    @Auditable(module = "__core__", action = "关闭登录二步验证", objectType = EntityTypes.SYS_USER)
    @PostMapping("/mfa/disable")
    public ResponseEntity<Void> mfaDisable(@Valid @RequestBody MfaDisableRequest req) {
        UserPrincipal p = UserContext.require();
        stepUpService().consume(p.userId(), req.stepUpToken());
        mfaService().disableLoginMfa(p.userId(), p.tenantId());
        return ResponseEntity.noContent().build();
    }

    public record MfaCodeRequest(@NotBlank String code) {
    }

    public record MfaDisableRequest(@NotBlank String stepUpToken) {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class MfaRecoveryCodesResponse {
        private List<String> recoveryCodes;

    }

    // ===== 改手机号 · step-up 前置身份验证后（发新手机码 → 校验并更新）=====

    @Operation(summary = "改手机号 · 发送新手机验证码", description = "需先经 step-up 验证身份（stepUpToken）")
    @Auditable(module = "__core__", action = "换绑手机·发送新号验证码", objectType = EntityTypes.SYS_USER)
    @PostMapping("/mobile/send-new-code")
    public OpsProfileService.MobileChangeStep sendNewMobileCode(@Valid @RequestBody SendNewMobileRequest req) {
        UserPrincipal p = UserContext.require();
        stepUpService().requireValid(p.userId(), req.stepUpToken());
        return opsProfileService.sendNewMobileCode(p.tenantId(), p.userId(), req.newMobile());
    }

    @Operation(summary = "改手机号 · 校验新手机验证码并更新", description = "校验通过后核销 step-up 票据")
    @Auditable(module = "__core__", action = "换绑手机·确认新号", objectType = EntityTypes.SYS_USER)
    @PostMapping("/mobile/change")
    public ResponseEntity<Void> confirmNewMobile(@Valid @RequestBody ChangeMobileRequest req) {
        UserPrincipal p = UserContext.require();
        stepUpService().consume(p.userId(), req.stepUpToken());
        opsProfileService.confirmNewMobile(p.tenantId(), p.userId(), req.code());
        return ResponseEntity.noContent().build();
    }

    public record SendNewMobileRequest(@NotBlank String stepUpToken, @NotBlank String newMobile) {
    }

    public record ChangeMobileRequest(@NotBlank String stepUpToken, @NotBlank String code) {
    }

    @Operation(summary = "通知偏好", description = "6 类别 × 通道开关；站内信强制开启(locked)")
    @GetMapping("/notification-preferences")
    public NotificationPreferenceView getNotificationPreferences() {
        UserPrincipal p = UserContext.require();
        return NotificationPreferenceView.from(
                notificationPreferenceService.getForUser(p.tenantId(), p.userId()));
    }

    @Operation(summary = "修改通知偏好", description = "批量 upsert；站内信关闭项被忽略")
    @Auditable(module = "__core__", action = "更新通知偏好", objectType = EntityTypes.SYS_USER)
    @PutMapping("/notification-preferences")
    public ResponseEntity<Void> updateNotificationPreferences(
            @Valid @RequestBody UpdateNotificationPreferencesRequest req) {
        UserPrincipal p = UserContext.require();
        List<NotificationPreferenceService.PreferenceItem> items = req.preferences().stream()
                .map(i -> new NotificationPreferenceService.PreferenceItem(i.category(), i.channel(), i.enabled()))
                .toList();
        notificationPreferenceService.update(p.tenantId(), p.userId(), items);
        return ResponseEntity.noContent().build();
    }
}
