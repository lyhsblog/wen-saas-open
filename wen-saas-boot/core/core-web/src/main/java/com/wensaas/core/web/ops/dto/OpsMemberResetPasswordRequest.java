package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 运营团队成员 · 重置密码请求体。 */
public record OpsMemberResetPasswordRequest(
        @Schema(description = "新密码（强度同新建）", example = "NewPass@2026!")
        @NotBlank
        @Size(min = 8, max = 64)
        String newPassword
) {}
