package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 代登录请求体。
 */
@Schema(description = "代登录请求")
public record ImpersonateRequest(
        @NotNull(message = "asUserId 不能为空")
        @Schema(description = "以该用户 ID 的身份代登录（主管理员 ID 或其他成员 ID）", type = "string", example = "7001")
        Long asUserId) {
}
