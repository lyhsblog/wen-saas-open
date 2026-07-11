package com.wensaas.core.impl.audit.aspect;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * ③ 数据变更日志的审计上下文注入器。
 *
 * <p>写事务（INSERT/UPDATE/DELETE）执行前，把 {@link RequestContext}（requestId）+
 * {@link UserContext}（actor/tenant）以事务级 GUC（{@code set_config(..., is_local=true)}）写到当前连接，
 * 供 {@code sys_audit_row_change()} 触发器 {@code current_setting('app.request_id', true)} 读取。
 *
 * <p>约束：
 * <ul>
 *   <li>仅在 Spring 实际事务内注入：autocommit 下 GUC 不跨语句、注入无意义 → 跳过（③ 行 requestId/actor 为 NULL，可接受兜底）。</li>
 *   <li>每事务只注入一次：首次写时注入并经 {@code afterCompletion} 复位标志，避免批量逐条重复往返。</li>
 *   <li>本拦截器只 {@code set_config}、不改写 SQL，在链中顺序无关紧要。</li>
 * </ul>
 */
public class AuditContextGucInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditContextGucInterceptor.class);

    /** 事务级"已注入"标志的 resource key（绑定到 {@link TransactionSynchronizationManager}）。 */
    private static final String STAMP_KEY = AuditContextGucInterceptor.class.getName() + ".STAMPED";

    private static final String INJECT_SQL =
            "select set_config('app.request_id', ?, true),"
                    + " set_config('app.actor_id', ?, true),"
                    + " set_config('app.tenant_id', ?, true)";

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) {
        SqlCommandType type = ms.getSqlCommandType();
        if (type != SqlCommandType.INSERT && type != SqlCommandType.UPDATE && type != SqlCommandType.DELETE) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return; // 非事务：GUC 不跨语句，注入无意义
        }
        if (Boolean.TRUE.equals(TransactionSynchronizationManager.getResource(STAMP_KEY))) {
            return; // 本事务已注入
        }

        String requestId = RequestContext.get();
        UserPrincipal principal = UserContext.get();
        try {
            Connection conn = executor.getTransaction().getConnection();
            try (PreparedStatement ps = conn.prepareStatement(INJECT_SQL)) {
                ps.setString(1, requestId == null ? "" : requestId);
                ps.setString(2, principal == null ? "" : Long.toString(principal.userId()));
                ps.setString(3, principal == null ? "" : Long.toString(principal.tenantId()));
                ps.execute();
            }
            markStamped();
        } catch (SQLException e) {
            log.warn("audit.guc.inject.fail requestId={} reason={}", requestId, e.getMessage());
        }
    }

    /** 标记本事务已注入，并注册 afterCompletion 复位（同线程下个事务可重新注入）。 */
    private void markStamped() {
        TransactionSynchronizationManager.bindResource(STAMP_KEY, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(STAMP_KEY)) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(STAMP_KEY);
                }
            }
        });
    }
}
