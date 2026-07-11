package com.wensaas.core.impl.audit.pending;

import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.AuditLogRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import com.wensaas.core.impl.audit.aspect.AuditableAspect;

/**
 * 审计失败事件的 HTTP 状态回填。
 *
 * <p>{@link AuditableAspect} 环绕 Controller 方法，异常路径记录失败事件时，异常尚未传到
 * {@code @RestControllerAdvice}，最终 HTTP 状态未知，故只能暂存（{@link PendingFailureAudits}）。
 * 本 filter 在 {@code chain.doFilter} 之后（advice 已写好响应状态）读 {@code response.getStatus()}
 * 回填真实状态后落库——避免把 422/409 等业务拒绝一律记成 500（旧行为：操作日志失真且 UI 误导）。
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE+5)}：位于 {@code DispatcherServlet} 之外即可在 finally 读到最终状态；
 * 暂存走请求属性（wrapper 委托共享），不依赖 {@code RequestContextHolder}（chain 返回后已解绑）。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class PendingAuditFlushFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PendingAuditFlushFilter.class);

    private final AuditLogRecorder recorder;

    public PendingAuditFlushFilter(AuditLogRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(req, resp);
        } finally {
            List<AuditEvent> pending = PendingFailureAudits.drain(req);
            if (!pending.isEmpty()) {
                int status = resp.getStatus();
                for (AuditEvent e : pending) {
                    try {
                        recorder.record(e.withStatusCode(status));
                    } catch (RuntimeException ex) {
                        log.warn("audit.flush.fail action={} module={} reason={}",
                                e.action(), e.module(), ex.getMessage());
                    }
                }
            }
        }
    }
}
