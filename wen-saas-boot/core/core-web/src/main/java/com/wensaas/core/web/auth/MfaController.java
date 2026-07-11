package com.wensaas.core.web.auth;

import com.wensaas.core.api.auth.login.MfaException;
import com.wensaas.core.api.auth.login.MfaMethod;
import com.wensaas.core.api.auth.permission.PublicEndpoint;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.impl.auth.login.AuthService;
import com.wensaas.core.impl.auth.mfa.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 * 运营后台二步验证。全部为公开端点，凭登录返回的 {@code preAuthToken} 完成验证。
 *
 * <p>流程：{@code POST /api/auth/login} → {mfaRequired, mfaEnabled, preAuthToken} →
 * 未配置走 {@link #setup} + {@link #verify}（首次强制配置，verify 返回 8 恢复码）；
 * 已配置 SMS/EMAIL 走 {@link #challenge}（发码）+ {@link #verify}，TOTP 直接 {@link #verify}。
 */
@Tag(name = "auth-mfa", description = "运营后台二步验证")
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private final ObjectProvider<MfaService> mfaServiceProvider;
    private final RefreshCookie refreshCookie;

    public MfaController(ObjectProvider<MfaService> mfaServiceProvider, RefreshCookie refreshCookie) {
        this.mfaServiceProvider = mfaServiceProvider;
        this.refreshCookie = refreshCookie;
    }

    private MfaService mfaService() {
        MfaService svc = mfaServiceProvider.getIfAvailable();
        if (svc == null) {
            throw new IllegalStateException(
                    "MFA 端点需启用通知模块（wen-saas-boot.notification.enabled=true）");
        }
        return svc;
    }

    @Operation(summary = "发起 MFA 配置（首次强制）",
            description = "选定通道：TOTP 返回密钥 + otpauth URI；SMS/EMAIL 向账号手机/邮箱发送动态码")
    @SecurityRequirements
    @PublicEndpoint
    @PostMapping("/setup")
    public MfaService.SetupChallenge setup(@Valid @RequestBody MfaSetupRequest req) {
        return mfaService().startSetup(req.preAuthToken(), parseMethod(req.method()));
    }

    @Operation(summary = "发送登录动态码",
            description = "已配置且通道为 SMS/EMAIL 时发送登录验证码；TOTP 返回 method 不发码")
    @SecurityRequirements
    @PublicEndpoint
    @PostMapping("/challenge")
    public MfaService.ChallengeInfo challenge(@Valid @RequestBody MfaChallengeRequest req) {
        return mfaService().sendLoginChallenge(req.preAuthToken());
    }

    @Operation(summary = "校验并完成登录",
            description = "校验 TOTP / 动态码 / 恢复码；通过即签发会话 token。首次配置额外返回一次性恢复码")
    @SecurityRequirements
    @PublicEndpoint
    @PostMapping("/verify")
    public MfaVerifyResponse verify(@Valid @RequestBody MfaVerifyRequest req, HttpServletResponse resp) {
        MfaService.VerifyOutcome outcome = mfaService().verify(req.preAuthToken(), req.code());
        AuthService.LoginResult login = outcome.login();
        UserPrincipal p = login.principal();
        refreshCookie.issue(resp, p.sessionId(), req.remember());
        return new MfaVerifyResponse(
                login.token(),
                login.expiresIn().toSeconds(),
                new LoginResponse.UserSummary(p.userId(), p.username(), p.tenantId()),
                outcome.recoveryCodes());
    }

    private static MfaMethod parseMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new MfaException(MfaException.Code.METHOD_REQUIRED, "未选择 MFA 通道");
        }
        try {
            return MfaMethod.valueOf(method.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new MfaException(MfaException.Code.METHOD_REQUIRED, "不支持的 MFA 通道: " + method);
        }
    }

    public record MfaSetupRequest(@NotBlank String preAuthToken, @NotBlank String method) {
    }

    public record MfaChallengeRequest(@NotBlank String preAuthToken) {
    }

    public record MfaVerifyRequest(@NotBlank String preAuthToken, @NotBlank String code,
                                   @Schema(description = "记住当前设备 30 天；缺省 / null 视为 false", nullable = true)
                                   Boolean rememberMe) {
        /** null 安全：未传或 null 时视为不记住。 */
        public boolean remember() {
            return Boolean.TRUE.equals(rememberMe);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class MfaVerifyResponse {
        private String token;
        private long expiresIn;
        private LoginResponse.UserSummary user;
        private List<String> recoveryCodes;

    }
}
