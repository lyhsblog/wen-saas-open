package com.wensaas.core.impl.async;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import org.slf4j.MDC;

import java.util.Map;

/**
 * UserContext + TenantContext + RequestContext + MDC 四件套快照。用于跨线程传递。
 *
 * <p>典型用法：
 * <pre>
 *   ContextSnapshot snap = ContextSnapshot.capture();
 *   executor.submit(() -&gt; {
 *       try (var token = snap.restore()) {
 *           // 子线程内 UserContext.get() / TenantContext.get() / RequestContext.get() / MDC 均已就绪
 *       }
 *   });
 * </pre>
 *
 * <p>{@link Restoration} 实现 {@link AutoCloseable}，try-with-resources 自动 clear；
 * close 会恢复子线程上下文到 restore 前状态（而非简单清空，嵌套友好）。
 */
public record ContextSnapshot(UserPrincipal user, Long tenantId, String requestId, Map<String, String> mdc) {

    /** 捕获当前线程的四件套快照。 */
    public static ContextSnapshot capture() {
        return new ContextSnapshot(
                UserContext.get(),
                TenantContext.get(),
                RequestContext.get(),
                MDC.getCopyOfContextMap());
    }

    /**
     * 将快照应用到当前（子）线程，返回 {@link Restoration} token；close 时恢复到 restore 前状态。
     */
    public Restoration restore() {
        UserPrincipal prevUser = UserContext.get();
        Long prevTenant = TenantContext.get();
        String prevRequest = RequestContext.get();
        Map<String, String> prevMdc = MDC.getCopyOfContextMap();

        if (user != null) {
            UserContext.set(user);
        } else {
            UserContext.clear();
        }
        if (tenantId != null) {
            TenantContext.set(tenantId);
        } else {
            TenantContext.clear();
        }
        if (requestId != null) {
            RequestContext.set(requestId);
        } else {
            RequestContext.clear();
        }
        if (mdc != null) {
            MDC.setContextMap(mdc);
        } else {
            MDC.clear();
        }

        return new Restoration(prevUser, prevTenant, prevRequest, prevMdc);
    }

    /** restore 返回的 token；close 时还原到 restore 前的上下文（嵌套友好）。 */
    public static final class Restoration implements AutoCloseable {
        private final UserPrincipal prevUser;
        private final Long prevTenant;
        private final String prevRequest;
        private final Map<String, String> prevMdc;

        Restoration(UserPrincipal prevUser, Long prevTenant, String prevRequest, Map<String, String> prevMdc) {
            this.prevUser = prevUser;
            this.prevTenant = prevTenant;
            this.prevRequest = prevRequest;
            this.prevMdc = prevMdc;
        }

        @Override
        public void close() {
            if (prevUser != null) UserContext.set(prevUser); else UserContext.clear();
            if (prevTenant != null) TenantContext.set(prevTenant); else TenantContext.clear();
            if (prevRequest != null) RequestContext.set(prevRequest); else RequestContext.clear();
            if (prevMdc != null) MDC.setContextMap(prevMdc); else MDC.clear();
        }
    }
}
