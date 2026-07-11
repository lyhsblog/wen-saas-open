package com.wensaas.core.web.publicapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/public/reset-password/by-token/{token} 请求体。
 */
public record SubmitResetPasswordRequest(

        @NotBlank
        String usernameInput,

        @NotBlank
        @Size(min = 8, max = 128, message = "密码长度 8-128 位")
        String newPassword
) {}
