package com.wensaas.core.impl.auth.session;

import com.wensaas.core.api.auth.user.AuthenticatedIdentity;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 会话存储接口。v1 实现 {@link RedisSessionStore}。
 */
public interface SessionStore {

    /**
     * 为已认证身份创建新 Session，返回 sessionId（UUID v4）。同步设置 TTL。
     */
    String createSession(AuthenticatedIdentity identity);

    /**
     * 查询 Session；不存在或已过期返回 empty。
     */
    Optional<Session> findBySessionId(String sessionId);

    /**
     * 立即失效一个 Session（退出登录 / 强踢）。
     */
    void invalidate(String sessionId);

    /**
     * 顺延一个 Session 及其用户索引集的 TTL（{@code /api/auth/refresh} 滑动续期时调用）。
     * Session 不存在则 no-op。同步更新 {@code lastAccessAt}。
     */
    void extendTtl(String sessionId, Duration ttl);

    /**
     * 为代登录创建新 Session，并在 {@code Session.originalSessionId} 中记录运营方原 session ID（反向链），
     * 同时快照运营操作人登录名到 {@code Session.operatorName}（供前端水印 / 标识溯源，不依赖原 session 后续存活）。
     *
     * @param identity          被代登录的目标用户身份
     * @param originalSessionId 运营方原 session ID；不能为 null
     * @param operatorName      发起代登录的运营操作人登录名（取自运营原 session）；可为 null（极端缺失时降级）
     * @return 新代登录 session 的 sessionId（UUID v4）
     */
    String createImpersonatedSession(AuthenticatedIdentity identity, String originalSessionId, String operatorName);

    /**
     * 列出某用户当前全部活跃 Session（按 {@code loginAt} 倒序）。读取期间顺手剔除已过期成员。
     * 用于运营/租户个人中心的"登录设备 / 会话"列表。
     */
    List<Session> findByUser(long tenantId, long userId);

    /**
     * 失效某用户全部 Session（改密后强制全设备下线）。
     */
    void invalidateAllByUser(long tenantId, long userId);

    /**
     * 失效某用户除 {@code keepSessionId} 外的全部 Session（"下线其他设备"）。
     */
    void invalidateAllByUserExcept(long tenantId, long userId, String keepSessionId);
}
