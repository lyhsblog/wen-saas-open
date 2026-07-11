package com.wensaas.core.web.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 代登录 nonce 兑换请求体。
 */
@Schema(description = "nonce 兑换请求")
public record RedeemImpersonateRequest(
        @NotBlank(message = "nonce 不能为空")
        @Schema(description = "代登录一次性 nonce，由运营端 POST impersonate 签发，5min 内有效", example = "550e8400-e29b-...")
        String nonce) {
}
