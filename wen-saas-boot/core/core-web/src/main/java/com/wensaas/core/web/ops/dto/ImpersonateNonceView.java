package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 代登录 nonce 签发结果。前端用 redeemNonce + targetTenantCode 拼跳转 URL，5min 内兑换。
 */
@Schema(description = "代登录 nonce 签发结果")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ImpersonateNonceView {
    @Schema(description = "一次性 nonce，前端带此参数跳转到租户端兑换 token")
    private String redeemNonce;
    @Schema(description = "目标租户标识，用于拼跳转目标 host（env 决定具体域名）", example = "acme")
    private String targetTenantCode;
    @Schema(description = "nonce 有效秒数", example = "300")
    private long expiresInSec;

}
