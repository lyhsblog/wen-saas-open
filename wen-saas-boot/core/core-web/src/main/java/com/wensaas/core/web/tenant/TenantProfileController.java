package com.wensaas.core.web.tenant;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.auth.oauth.OAuthBindingService;
import com.wensaas.core.api.tenant.selfservice.TenantProfileService;
import com.wensaas.core.impl.auth.mfa.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import java.time.LocalDate;
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
 * 租户后台 · 个人中心（原型 tenant-profile.jsx，PRD 03/09/22）。
 *
 * <p>前缀 {@code /api/v1/base/profile}，面向租户内任意已登录成员管理自己的账号；
 * 主体取自 {@link UserContext}，无需额外权限点（自助）。与运营侧 {@code OpsProfileController} 平行。
 */
@Tag(name = "tenant-profile", description = "租户后台 · 个人中心")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/profile")
public class TenantProfileController {

    private final TenantProfileService profileService;
    private final ObjectProvider<MfaService> mfaServiceProvider;
    private final ObjectProvider<com.wensaas.core.impl.auth.stepup.StepUpService> stepUpServiceProvider;
    private final OAuthBindingService oauthBindingService;

    public TenantProfileController(TenantProfileService profileService,
                                   ObjectProvider<MfaService> mfaServiceProvider,
                                   ObjectProvider<com.wensaas.core.impl.auth.stepup.StepUpService> stepUpServiceProvider,
                                   OAuthBindingService oauthBindingService) {
        this.profileService = profileService;
        this.mfaServiceProvider = mfaServiceProvider;
        this.stepUpServiceProvider = stepUpServiceProvider;
        this.oauthBindingService = oauthBindingService;
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

    // ===== 只读视图 =====

    @Operation(summary = "基本信息 tab")
    @GetMapping("/basic")
    public TenantProfileService.Basic getBasic() {
        UserPrincipal p = UserContext.require();
        return profileService.getBasic(p.tenantId(), p.userId());
    }

    @Operation(summary = "组织与角色 tab", description = "部门 / 平台角色 / 账号状态 / 数据范围（只读）")
    @GetMapping("/work")
    public TenantProfileService.Work getWork() {
        UserPrincipal p = UserContext.require();
        return profileService.getWork(p.tenantId(), p.userId(), p.tenantOwner());
    }

    @Operation(summary = "安全设置 tab", description = "会话超时 / 锁定阈值 / MFA 状态（MFA 为租户 opt-in）")
    @GetMapping("/security")
    public TenantProfileService.Security getSecurity() {
        UserPrincipal p = UserContext.require();
        return profileService.getSecurity(p.tenantId(), p.userId());
    }

    // ===== 写操作 =====

    @Operation(summary = "修改基本信息", description = "可自助项：性别 / 生日 / 头像（姓名不可自助改，仅管理员在用户管理处变更）")
    @Auditable(module = "__core__", action = "编辑个人资料", objectType = EntityTypes.SYS_USER)
    @PutMapping
    public ResponseEntity<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        UserPrincipal p = UserContext.require();
        profileService.updateProfile(p.tenantId(), p.userId(),
                new TenantProfileService.ProfileUpdate(
                        req.gender(), req.birthday(), req.avatarFileId()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "上传头像（≤1MB，JPG/PNG）", description = "落文件中心并回写 avatar_file_id，返回新 fileId + 签名 URL")
    @Auditable(module = "__core__", action = "上传头像", objectType = "")
    @PostMapping("/avatar")
    public TenantProfileService.AvatarResult uploadAvatar(@RequestParam("file") MultipartFile file)
            throws IOException {
        UserPrincipal p = UserContext.require();
        return profileService.uploadAvatar(p.tenantId(), p.userId(),
                new TenantProfileService.AvatarUpload(
                        file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream()));
    }

    @Operation(summary = "修改密码", description = "step-up 验证身份 + 强度校验；成功后强制下线全部设备，需重新登录")
    @Auditable(module = "__core__", action = "修改密码", objectType = EntityTypes.SYS_USER)
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        UserPrincipal p = UserContext.require();
        stepUpService().consume(p.userId(), req.stepUpToken());
        profileService.changePassword(p.tenantId(), p.userId(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "我的会话/设备", description = "列出当前用户全部活跃会话，标记当前会话")
    @GetMapping("/sessions")
    public List<TenantProfileService.SessionItem> listSessions() {
        UserPrincipal p = UserContext.require();
        return profileService.listSessions(p.tenantId(), p.userId(), p.sessionId());
    }

    @Operation(summary = "下线指定会话", description = "不能下线当前会话（409）；目标不属于本人返回 404")
    @Auditable(module = "__core__", action = "下线会话", objectType = EntityTypes.SYS_USER, objectIdExpr = "#sessionId")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        UserPrincipal p = UserContext.require();
        profileService.revokeSession(p.tenantId(), p.userId(), p.sessionId(), sessionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "下线其他设备", description = "下线除当前会话外的全部会话")
    @Auditable(module = "__core__", action = "下线其他设备", objectType = EntityTypes.SYS_USER)
    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<Void> revokeOtherSessions() {
        UserPrincipal p = UserContext.require();
        profileService.revokeOtherSessions(p.tenantId(), p.userId(), p.sessionId());
        return ResponseEntity.noContent().build();
    }

    // ===== 第三方账号绑定（OAuth）=====

    @Operation(summary = "我的第三方账号绑定列表")
    @GetMapping("/oauth-bindings")
    public List<OAuthBindingService.Binding> listOAuthBindings() {
        UserPrincipal p = UserContext.require();
        return oauthBindingService.listByUser(p.tenantId(), p.userId());
    }

    @Operation(summary = "绑定第三方账号", description = "已登录态主动绑定：用渠道 authCode 绑定到当前账号")
    @Auditable(module = "__core__", action = "绑定第三方账号", objectType = EntityTypes.SYS_USER)
    @PostMapping("/oauth-bindings/{provider}")
    public ResponseEntity<Void> bindOAuth(@PathVariable String provider,
                                          @Valid @RequestBody OAuthBindBody req) {
        UserPrincipal p = UserContext.require();
        oauthBindingService.bind(p.tenantId(), p.userId(), provider, req.authCode());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "解绑第三方账号", description = "解除当前账号的指定渠道绑定（幂等）")
    @Auditable(module = "__core__", action = "解绑第三方账号", objectType = EntityTypes.SYS_USER)
    @DeleteMapping("/oauth-bindings/{provider}")
    public ResponseEntity<Void> unbindOAuth(@PathVariable String provider) {
        UserPrincipal p = UserContext.require();
        oauthBindingService.unbind(p.tenantId(), p.userId(), provider);
        return ResponseEntity.noContent().build();
    }

    // ===== 安全验证：验证器绑定（独立于登录开关） + 登录二步验证开关 =====

    @Operation(summary = "MFA 状态", description = "登录二步验证开关 / 验证器绑定态 / 推导的登录验证通道")
    @GetMapping("/mfa")
    public MfaStatusView mfaStatus() {
        UserPrincipal p = UserContext.require();
        MfaService.Status s = mfaService().status(p.userId());
        return new MfaStatusView(false, s.loginMfaEnabled(),
                s.loginMethod() == null ? null : s.loginMethod().name());
    }

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
    public ResponseEntity<Void> totpBindConfirm(@Valid @RequestBody MfaConfirmRequest req) {
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
    public MfaRecoveryCodesResponse mfaEnableConfirm(@Valid @RequestBody MfaConfirmRequest req) {
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

    // ===== 改手机号 · step-up 前置身份验证后 =====

    @Operation(summary = "改手机号 · 发送新手机验证码", description = "需先经 step-up 验证身份（stepUpToken）")
    @Auditable(module = "__core__", action = "换绑手机·发送新号验证码", objectType = EntityTypes.SYS_USER)
    @PostMapping("/mobile/send-new-code")
    public TenantProfileService.MobileChangeStep sendNewMobileCode(@Valid @RequestBody SendNewMobileRequest req) {
        UserPrincipal p = UserContext.require();
        stepUpService().requireValid(p.userId(), req.stepUpToken());
        return profileService.sendNewMobileCode(p.tenantId(), p.userId(), req.newMobile());
    }

    @Operation(summary = "改手机号 · 校验新手机验证码并更新", description = "校验通过后核销 step-up 票据")
    @Auditable(module = "__core__", action = "换绑手机·确认新号", objectType = EntityTypes.SYS_USER)
    @PostMapping("/mobile/change")
    public ResponseEntity<Void> confirmNewMobile(@Valid @RequestBody ChangeMobileRequest req) {
        UserPrincipal p = UserContext.require();
        stepUpService().consume(p.userId(), req.stepUpToken());
        profileService.confirmNewMobile(p.tenantId(), p.userId(), req.code());
        return ResponseEntity.noContent().build();
    }

    // ===== 改邮箱 · step-up 前置身份验证后（租户端自助）=====

    @Operation(summary = "改邮箱 · 发送新邮箱验证码", description = "需先经 step-up 验证身份（stepUpToken）")
    @Auditable(module = "__core__", action = "换绑邮箱·发送新邮箱验证码", objectType = EntityTypes.SYS_USER)
    @PostMapping("/email/send-new-code")
    public TenantProfileService.MobileChangeStep sendNewEmailCode(@Valid @RequestBody SendNewEmailRequest req) {
        UserPrincipal p = UserContext.require();
        stepUpService().requireValid(p.userId(), req.stepUpToken());
        return profileService.sendNewEmailCode(p.tenantId(), p.userId(), req.newEmail());
    }

    @Operation(summary = "改邮箱 · 校验新邮箱验证码并更新", description = "校验唯一性通过后核销 step-up 票据")
    @Auditable(module = "__core__", action = "换绑邮箱·确认新邮箱", objectType = EntityTypes.SYS_USER)
    @PostMapping("/email/change")
    public ResponseEntity<Void> confirmNewEmail(@Valid @RequestBody ChangeEmailRequest req) {
        UserPrincipal p = UserContext.require();
        stepUpService().consume(p.userId(), req.stepUpToken());
        profileService.confirmNewEmail(p.tenantId(), p.userId(), req.code());
        return ResponseEntity.noContent().build();
    }

    // ===== 请求 / 响应 DTO =====

    public record UpdateProfileRequest(
            Integer gender,
            LocalDate birthday,
            String avatarFileId) {
    }

    public record ChangePasswordRequest(
            @NotBlank String stepUpToken,
            @NotBlank String newPassword) {
    }

    public record MfaConfirmRequest(@NotBlank String code) {
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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class MfaStatusView {
        private boolean required;
        /** 登录二步验证开关。 */
        private boolean enabled;
        /** 推导的登录验证通道：TOTP / EMAIL；未开启为空。 */
        private String method;

    }

    public record SendNewMobileRequest(@NotBlank String stepUpToken, @NotBlank String newMobile) {
    }

    public record ChangeMobileRequest(@NotBlank String stepUpToken, @NotBlank String code) {
    }

    public record SendNewEmailRequest(@NotBlank String stepUpToken, @NotBlank String newEmail) {
    }

    public record ChangeEmailRequest(@NotBlank String stepUpToken, @NotBlank String code) {
    }

    public record OAuthBindBody(@NotBlank String authCode) {
    }
}
