package com.wensaas.core.web.security;

import com.wensaas.core.api.auth.login.MfaMethod;
import com.wensaas.core.api.auth.stepup.StepUpException;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.impl.auth.stepup.StepUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 敏感操作 step-up（前置身份验证）通用端点。运营 / 租户账号共用一套（凭 JWT 自助）。
 *
 * <p>前缀 {@code /api/v1/security/stepup}，非 {@code /api/v1/ops}·{@code /api/v1/base}，
 * 故 OpsAuthWebFilter / 租户活跃度过滤器都不介入，只需登录态即可访问。
 *
 * <p>用法：{@code GET /channels}（拿可用通道 + 掩码）→ 选通道 {@code POST /challenge}（短信/邮件发码；
 * TOTP 免）→ {@code POST /verify}（校验→拿 stepUpToken）→ 把 token 交给改密码 / 改手机 / 改邮箱端点。
 */
@Tag(name = "security-stepup", description = "敏感操作前置身份验证")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/security/stepup")
public class StepUpController {

    private final ObjectProvider<StepUpService> stepUpServiceProvider;

    public StepUpController(ObjectProvider<StepUpService> stepUpServiceProvider) {
        this.stepUpServiceProvider = stepUpServiceProvider;
    }

    private StepUpService stepUpService() {
        StepUpService svc = stepUpServiceProvider.getIfAvailable();
        if (svc == null) {
            throw new IllegalStateException(
                    "step-up 端点需启用通知模块（wen-saas-boot.notification.enabled=true）");
        }
        return svc;
    }

    @Operation(summary = "可用验证通道", description = "各通道是否可用 + 掩码目标；未配置通道前端置灰")
    @GetMapping("/channels")
    public StepUpService.Channels channels() {
        UserPrincipal p = UserContext.require();
        return stepUpService().availability(p.userId());
    }

    @Operation(summary = "发起验证挑战", description = "短信 / 邮件发送动态码；TOTP 无需发码")
    @PostMapping("/challenge")
    public StepUpService.Challenge challenge(@Valid @RequestBody ChannelRequest req) {
        UserPrincipal p = UserContext.require();
        return stepUpService().challenge(p.userId(), p.tenantId(), parseMethod(req.channel()));
    }

    @Operation(summary = "校验身份并签发票据", description = "校验动态码通过 → 返回短时 stepUpToken")
    @PostMapping("/verify")
    public StepUpService.Ticket verify(@Valid @RequestBody VerifyRequest req) {
        UserPrincipal p = UserContext.require();
        return stepUpService().verify(p.userId(), p.tenantId(), parseMethod(req.channel()), req.code());
    }

    private static MfaMethod parseMethod(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new StepUpException(StepUpException.Code.METHOD_REQUIRED, "未选择验证通道");
        }
        try {
            return MfaMethod.valueOf(channel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new StepUpException(StepUpException.Code.METHOD_REQUIRED, "不支持的验证通道: " + channel);
        }
    }

    public record ChannelRequest(@NotBlank String channel) {
    }

    public record VerifyRequest(@NotBlank String channel, @NotBlank String code) {
    }
}
