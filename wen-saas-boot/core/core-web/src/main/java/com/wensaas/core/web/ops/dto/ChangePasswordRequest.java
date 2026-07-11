package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 自助修改密码。身份由 step-up 票据证明（{@code stepUpToken}），强度规则由后端
 * {@code PasswordStrengthValidator} 二次校验。
 */
public record ChangePasswordRequest(
        @NotBlank String stepUpToken,
        @NotBlank @Size(max = 128) String newPassword) {
}
