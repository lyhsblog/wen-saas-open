package com.wensaas.core.web.publicapi;

import com.wensaas.core.api.auth.permission.PublicEndpoint;
import com.wensaas.core.impl.auth.password.PasswordResetPublicService;
import com.wensaas.core.web.publicapi.dto.ResetPasswordResult;
import com.wensaas.core.web.publicapi.dto.ResetPasswordView;
import com.wensaas.core.web.publicapi.dto.SubmitResetPasswordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 公开密码重置接口。无鉴权，主管理员首次设密码 / 找回密码用。
 *
 * <p>支撑前端 {@code /reset-password/{token}} 页面（租户子域）。
 *
 * <p>仅负责 HTTP 编解码；token 校验 / 密码哈希 / 账号激活等业务编排（含事务与跨租户作用域）下沉到
 * {@link PasswordResetPublicService}。
 */
@Tag(name = "public-reset-password", description = "公开密码重置")
@SecurityRequirements
@PublicEndpoint
@RestController
@RequestMapping("/api/v1/public/reset-password")
public class PasswordResetPublicController {

    private final PasswordResetPublicService passwordResetPublicService;

    public PasswordResetPublicController(PasswordResetPublicService passwordResetPublicService) {
        this.passwordResetPublicService = passwordResetPublicService;
    }

    @Operation(summary = "查询密码重置 token 状态",
            description = "返回租户名、账号掩码、过期时间及 token 状态（VALID/EXPIRED/USED/INVALIDATED）")
    @GetMapping("/by-token/{token}")
    public ResetPasswordView checkToken(@PathVariable String token) {
        PasswordResetPublicService.TokenStatus s = passwordResetPublicService.checkToken(token);
        String expiresAt = s.expiresAt().atOffset(ZoneOffset.of("+08:00"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new ResetPasswordView(s.tenantName(), s.tenantCode(), s.usernameMask(),
                expiresAt, s.status(), s.purpose());
    }

    @Operation(summary = "提交密码重置",
            description = "校验 token + 账号 + 密码强度 → 更新 sys_user.password_hash + 激活账号 → 标记 token 已使用")
    @PostMapping("/by-token/{token}")
    public ResetPasswordResult submitReset(@PathVariable String token,
                                           @Valid @RequestBody SubmitResetPasswordRequest req) {
        PasswordResetPublicService.ResetOutcome outcome =
                passwordResetPublicService.submitReset(token, req.usernameInput(), req.newPassword());
        return new ResetPasswordResult(outcome.tenantCode());
    }
}
