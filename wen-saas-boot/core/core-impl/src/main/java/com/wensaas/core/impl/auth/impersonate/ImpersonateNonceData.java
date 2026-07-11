package com.wensaas.core.impl.auth.impersonate;

import java.time.Instant;

/**
 * 代登录一次性 nonce 携带的数据（存 Redis，TTL=5min，单次兑换）。
 *
 * @param targetTenantId   被代登录的租户 ID
 * @param asUserId         被代登录的目标用户 ID
 * @param operatorUserId   发起代登录的运营用户 ID
 * @param operatorSessionId 运营方原 session ID（兑换时用于反向链校验 + 审计）
 * @param createdAt        nonce 签发时刻
 */
public record ImpersonateNonceData(
        long targetTenantId,
        long asUserId,
        long operatorUserId,
        String operatorSessionId,
        Instant createdAt) {
}
