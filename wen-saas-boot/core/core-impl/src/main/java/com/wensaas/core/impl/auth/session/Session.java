package com.wensaas.core.impl.auth.session;

import java.time.Instant;

/**
 * 持久化在 Redis 的会话状态。
 *
 * @param sessionId          会话 ID（UUID v4）
 * @param userId             用户 ID
 * @param tenantId           租户 ID；0 = 运营
 * @param username           登录账号
 * @param tenantOwner        是否租户主管理员（{@code sys_tenant.main_admin_user_id == userId}）。
 *                           用于权限切面 / 数据权限拦截器快速跳过。
 * @param mainDeptId         主部门 ID（{@code sys_user_dept.is_main=1}）。可能为 null：未归属部门 / 运营账号。
 * @param loginAt            登录时刻
 * @param lastAccessAt       最近一次被认证过滤器命中的时刻（v1 不滑动续期，仅做监控）
 * @param originalSessionId  非 null 时表示本会话为代登录会话，值为运营方的原 session ID（反向链）。
 *                           退出代登录后此 session 被立刻 invalidate；该字段不出现在 JWT 中。
 * @param operatorName       仅代登录会话非 null：发起代登录的运营操作人登录名，于代登录 session 创建时
 *                           从运营原 session 快照而来（不依赖原 session 后续存活），供前端水印 / 标识溯源展示。
 * @param realName           本会话用户真实姓名（{@code sys_user.real_name}）快照，登录时填充。
 *                           随 UserPrincipal 还原供审计 / 水印读内存取用；退出代登录审计经运营原会话取此值。
 *                           可能为 null：姓名为空 / 历史会话。
 * @param clientIp           登录时客户端 IP（优先 X-Forwarded-For 首段，否则 remoteAddr）。供个人中心
 *                           「登录设备 / 会话」识别陌生设备。可能为 null：无请求上下文 / 历史会话。
 * @param userAgent          登录时 User-Agent 原文（截断 ≤512）。前端/服务端据此解析浏览器 + 系统展示。
 *                           可能为 null：缺 UA 头 / 历史会话。
 */
public record Session(
        String sessionId,
        long userId,
        long tenantId,
        String username,
        boolean tenantOwner,
        Long mainDeptId,
        Instant loginAt,
        Instant lastAccessAt,
        String originalSessionId,
        String operatorName,
        String realName,
        String clientIp,
        String userAgent) {

    /** 兼容旧 11 参构造：clientIp / userAgent 置 null。 */
    public Session(String sessionId, long userId, long tenantId, String username, boolean tenantOwner,
                   Long mainDeptId, Instant loginAt, Instant lastAccessAt, String originalSessionId,
                   String operatorName, String realName) {
        this(sessionId, userId, tenantId, username, tenantOwner, mainDeptId, loginAt, lastAccessAt,
                originalSessionId, operatorName, realName, null, null);
    }

    /** 兼容旧 10 参构造：realName / clientIp / userAgent 置 null。 */
    public Session(String sessionId, long userId, long tenantId, String username, boolean tenantOwner,
                   Long mainDeptId, Instant loginAt, Instant lastAccessAt, String originalSessionId,
                   String operatorName) {
        this(sessionId, userId, tenantId, username, tenantOwner, mainDeptId, loginAt, lastAccessAt,
                originalSessionId, operatorName, null, null, null);
    }
}
