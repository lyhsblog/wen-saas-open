package com.wensaas.core.web.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求 DTO。
 *
 * @param username   登录账号
 * @param password   明文密码（HTTPS 传输，不入日志）
 * @param tenantCode 可选：租户编码；非空时按 sys_tenant.code 反查租户并仅在该租户内匹配账号（多租户重名账号场景），
 *                   缺省则按运营账号（tenant_id=0）匹配
 * @param rememberMe 记住当前设备：true 时 refresh token 有效期 30d，否则用默认会话时长
 */
@Schema(description = "登录请求")
public record LoginRequest(
        @Schema(description = "登录账号", example = "alice", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 4, max = 64) String username,

        @Schema(description = "明文密码（HTTPS 传输，不入日志）", example = "P@ssw0rd",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 6, max = 128) String password,

        @Schema(description = "可选：租户编码；非空时按该租户匹配账号（租户账号登录必传），缺省按运营账号匹配", nullable = true)
        String tenantCode,

        @Schema(description = "记住当前设备 30 天（refresh token 有效期）；缺省 / null 视为 false", example = "false",
                nullable = true)
        Boolean rememberMe
) {
    /** null 安全：未传或 null 时视为不记住。 */
    public boolean remember() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
