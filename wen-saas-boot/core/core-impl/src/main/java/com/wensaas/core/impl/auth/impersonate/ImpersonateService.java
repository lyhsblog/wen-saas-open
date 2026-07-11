package com.wensaas.core.impl.auth.impersonate;

import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.impl.auth.login.AuthService;

/**
 * 代登录服务门面。
 *
 * <p>三段式流程：
 * <ol>
 *   <li>运营调 {@link #startImpersonation} → 返回 nonce（5min TTL，单次使用）</li>
 *   <li>前端跳转到租户端，租户端调公开接口 {@link #redeemNonce} → 换取代登录 token</li>
 *   <li>退出时调 {@link #exitImpersonation} → 销毁代登录 session 并写双向审计</li>
 * </ol>
 *
 * <p>运营端 / 租户端在不同域名，浏览器存储天然隔离：不存在代登录 token 覆盖运营 token 的问题，
 * 运营原 session 全程未被改动，因此退出代登录无需「回退/重签运营 token」，
 * tenant-web 只需主动销毁代登录 session 即可。
 */
public interface ImpersonateService {

    /**
     * 代登录 nonce 签发结果。
     *
     * @param redeemNonce      一次性 nonce，交给前端拼跳转 URL 后带给租户端
     * @param targetTenantCode 目标租户标识（ops-web 用于拼跳转目标 host）
     * @param expiresInSec     nonce 有效秒数（固定 300）
     */
    record NonceIssued(String redeemNonce, String targetTenantCode, long expiresInSec) {
    }

    /**
     * 运营发起代登录：校验目标租户与目标用户，签发一次性 nonce。
     * 调用方须为运营账号（tenant_id=0），由 OpsAuthWebFilter 保证；不允许在代登录中嵌套代登录。
     *
     * @param tenantId 目标租户 ID
     * @param asUserId 以该目标用户身份代登录（必填，不传 400）
     */
    @CrossTenantQuery
    NonceIssued startImpersonation(long tenantId, long asUserId);

    /**
     * 公开兑换：租户端用 nonce 换取代登录 token。无需携带 Authorization header。
     * nonce 单次使用；兑换成功后返回代登录 session 的 token 与用户信息。
     */
    @CrossTenantQuery
    AuthService.LoginResult redeemNonce(String nonce);

    /**
     * 退出代登录：销毁当前代登录 session，并写一条 impersonate-exit 审计。
     * 调用方须为代登录态（session.originalSessionId 非空）；运营原 session 已过期则 410
     * （仍视为有效退出，但提示运营需重新登录）。
     *
     * @param currentSessionId 当前请求的 session ID（从 JWT 解析而来）
     */
    void exitImpersonation(String currentSessionId);
}
