package com.wensaas.core.api.audit;

import java.time.Instant;

/**
 * 登录日志事件（{@code sys_login_log} 一行的契约对照）。
 *
 * <p>由 {@code AuthServiceImpl} 在 login 成功 / 失败 / logout 三处调
 * {@code AuditLogRecorder.recordLogin(...)} 投递。
 *
 * @param id              雪花 ID
 * @param tenantId        操作方租户；运营登录时 = 0
 * @param requestId       请求 ID
 * @param operatorId      用户 ID；登录失败（账号不存在）时 null
 * @param operatorName    用户姓名快照
 * @param operatorDeptId  主部门快照
 * @param account         用户输入账号原文（用于排查 / 数据权限）
 * @param loginMethod     password / sms / wechat / wecom / dingtalk
 * @param action          login / logout / session_expired
 * @param result          success / fail
 * @param failReason      bad_credentials / disabled / locked / oauth_failed / other
 * @param ip              来源 IP
 * @param userAgent       UA
 * @param createdAt       事件发生时刻
 */
public record LoginAuditEvent(
        Long id,
        Long tenantId,
        String requestId,
        Long operatorId,
        String operatorName,
        Long operatorDeptId,
        String account,
        String loginMethod,
        String action,
        String result,
        String failReason,
        String ip,
        String userAgent,
        Instant createdAt) {
}
