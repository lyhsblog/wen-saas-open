package com.wensaas.core.impl.audit.pending;

import com.wensaas.core.api.audit.AuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import com.wensaas.core.impl.audit.aspect.AuditableAspect;

/**
 * 暂存「失败但最终 HTTP 状态待定」的审计事件，挂在当前请求属性上。
 *
 * <p>{@link AuditableAspect} 异常路径放入（此刻异常尚未传到 {@code @RestControllerAdvice}，状态未定），
 * {@link PendingAuditFlushFilter} 在响应提交后读 {@code response.getStatus()} 回填真实状态再落库。
 *
 * <p>用请求属性而非 ThreadLocal：跨 async dispatch 仍可靠；wrapper 的 setAttribute 委托到被包装请求，
 * 故内层切面写、外层 filter 读互相可见。
 */
public final class PendingFailureAudits {

    static final String ATTR = PendingFailureAudits.class.getName() + ".PENDING";

    private PendingFailureAudits() {
    }

    /** 把失败事件挂到当前请求；无 servlet 请求上下文（MQ/@Async）时返回 false，调用方应内联兜底记录。 */
    public static boolean stash(AuditEvent event) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return false;
        }
        HttpServletRequest req = sra.getRequest();
        @SuppressWarnings("unchecked")
        List<AuditEvent> list = (List<AuditEvent>) req.getAttribute(ATTR);
        if (list == null) {
            list = new ArrayList<>(2);
            req.setAttribute(ATTR, list);
        }
        list.add(event);
        return true;
    }

    /** 取出并清空当前请求暂存的失败事件；无则返回空列表。 */
    public static List<AuditEvent> drain(HttpServletRequest req) {
        Object v = req.getAttribute(ATTR);
        if (v == null) {
            return List.of();
        }
        req.removeAttribute(ATTR);
        @SuppressWarnings("unchecked")
        List<AuditEvent> list = (List<AuditEvent>) v;
        return list;
    }
}
