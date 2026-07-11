package com.wensaas.core.impl.audit.aspect;

import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserQueryService;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;
import com.wensaas.core.impl.audit.pending.PendingAuditFlushFilter;
import com.wensaas.core.impl.audit.pending.PendingFailureAudits;

/**
 * {@link Auditable} 注解切面。
 *
 * <p>{@code @Order(20)}：晚于 {@code CrossTenantQueryAspect(0)} / {@code RequirePermissionAspect(10)}，
 * 让权限通过后才记录。
 *
 * <p>流程：
 * <ol>
 *   <li>{@code pjp.proceed()}；异常路径仍记录失败事件后 rethrow</li>
 *   <li>SpEL 求值 {@code objectIdExpr / objectDescExpr}（注入方法参数名 + {@code #result}）</li>
 *   <li>{@link AuditLogRecorder#record} 投递异步管道</li>
 * </ol>
 *
 * <p>字段级"改前/改后"不在此捕获——统一由 ③ 数据变更日志（PG 触发器）按 requestId 记录。
 *
 * <p>切面自身异常吞掉（log.warn），不影响业务方返回。
 */
@Aspect
@Order(20)
public class AuditableAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditableAspect.class);
    private static final int ERROR_MESSAGE_MAX_LEN = 1024;

    private final AuditLogRecorder recorder;
    private final UserQueryService userQuery;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramNames = new DefaultParameterNameDiscoverer();

    public AuditableAspect(AuditLogRecorder recorder, UserQueryService userQuery) {
        this.recorder = recorder;
        this.userQuery = userQuery;
    }

    @Around("@annotation(com.wensaas.core.api.audit.Auditable)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Auditable ann = AnnotatedElementUtils.findMergedAnnotation(method, Auditable.class);
        if (ann == null) {
            return pjp.proceed();
        }

        Object[] args = pjp.getArgs();

        long t0 = System.nanoTime();
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable ex) {
            int durationMs = (int) ((System.nanoTime() - t0) / 1_000_000);
            AuditEvent failure = buildFailureEvent(ann, method, args, ex, durationMs);
            // 失败的最终 HTTP 状态由 @RestControllerAdvice 决定（晚于本切面），Web 请求里先暂存，
            // 由 PendingAuditFlushFilter 在响应提交后回填真实状态；非 Web 线程(MQ/@Async)无 HTTP 状态，内联兜底(500)。
            if (!PendingFailureAudits.stash(failure)) {
                recordSafely(failure);
            }
            throw ex;
        }

        int durationMs = (int) ((System.nanoTime() - t0) / 1_000_000);
        recordSafely(buildSuccessEvent(ann, method, args, result, durationMs));
        return result;
    }

    private AuditEvent buildSuccessEvent(Auditable ann, Method method, Object[] args, Object result,
                                          int durationMs) {
        HttpInfo http = currentHttpInfo();
        return new AuditEvent(
                null,
                currentTenantId(),
                evalLong(ann.targetTenantIdExpr(), method, args, result),
                currentRequestId(),
                currentOperatorId(),
                currentOperatorName(),
                currentOperatorDeptId(),
                ann.module(),
                ann.action(),
                ann.objectType(),
                evalString(ann.objectIdExpr(), method, args, result),
                evalString(ann.objectDescExpr(), method, args, result),
                http.method(),
                http.path(),
                200,
                durationMs,
                null,
                http.ip(),
                http.userAgent(),
                Instant.now(),
                AuditObjects.drain());
    }

    private AuditEvent buildFailureEvent(Auditable ann, Method method, Object[] args,
                                          Throwable ex, int durationMs) {
        String msg = ex.getMessage();
        if (msg != null && msg.length() > ERROR_MESSAGE_MAX_LEN) {
            msg = msg.substring(0, ERROR_MESSAGE_MAX_LEN);
        }
        HttpInfo http = currentHttpInfo();
        return new AuditEvent(
                null,
                currentTenantId(),
                evalLong(ann.targetTenantIdExpr(), method, args, null),
                currentRequestId(),
                currentOperatorId(),
                currentOperatorName(),
                currentOperatorDeptId(),
                ann.module(),
                ann.action(),
                ann.objectType(),
                evalString(ann.objectIdExpr(), method, args, null),
                evalString(ann.objectDescExpr(), method, args, null),
                http.method(),
                http.path(),
                500,
                durationMs,
                msg,
                http.ip(),
                http.userAgent(),
                Instant.now(),
                AuditObjects.drain());
    }

    Long evalLong(String expr, Method method, Object[] args, Object result) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        try {
            EvaluationContext ctx = new StandardEvaluationContext();
            String[] names = paramNames.getParameterNames(method);
            if (names != null) {
                for (int i = 0; i < names.length && i < args.length; i++) {
                    ((StandardEvaluationContext) ctx).setVariable(names[i], args[i]);
                }
            }
            ((StandardEvaluationContext) ctx).setVariable("result", result);
            Object val = parser.parseExpression(expr).getValue(ctx);
            if (val == null) return null;
            if (val instanceof Long l) return l;
            if (val instanceof Number n) return n.longValue();
            return Long.parseLong(val.toString());
        } catch (RuntimeException e) {
            log.warn("audit.spel.fail expr={} method={}#{} reason={}",
                    expr, method.getDeclaringClass().getSimpleName(), method.getName(), e.getMessage());
            return null;
        }
    }

    String evalString(String expr, Method method, Object[] args, Object result) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        try {
            EvaluationContext ctx = new StandardEvaluationContext();
            String[] names = paramNames.getParameterNames(method);
            if (names != null) {
                for (int i = 0; i < names.length && i < args.length; i++) {
                    ((StandardEvaluationContext) ctx).setVariable(names[i], args[i]);
                }
            }
            ((StandardEvaluationContext) ctx).setVariable("result", result);
            Object val = parser.parseExpression(expr).getValue(ctx);
            return val == null ? null : val.toString();
        } catch (RuntimeException e) {
            log.warn("audit.spel.fail expr={} method={}#{} reason={}",
                    expr, method.getDeclaringClass().getSimpleName(), method.getName(), e.getMessage());
            return null;
        }
    }

    /** 当前请求的 HTTP 元信息（method / path / ip / userAgent）；非 Web 线程（MQ/@Async）取不到时全 null。 */
    private record HttpInfo(String method, String path, String ip, String userAgent) {
        static final HttpInfo EMPTY = new HttpInfo(null, null, null, null);
    }

    private static HttpInfo currentHttpInfo() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return HttpInfo.EMPTY;
        }
        HttpServletRequest req = sra.getRequest();
        return new HttpInfo(
                req.getMethod(),
                req.getRequestURI(),
                resolveClientIp(req),
                req.getHeader("User-Agent"));
    }

    /** 取真实来源 IP：优先 X-Forwarded-For 首跳，否则 remoteAddr（与 AppKeyAuthenticationFilter 一致）。 */
    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma < 0 ? xff : xff.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }

    private void recordSafely(AuditEvent event) {
        try {
            recorder.record(event);
        } catch (RuntimeException e) {
            log.warn("audit.record.fail action={} module={} reason={}",
                    event.action(), event.module(), e.getMessage());
        }
    }

    private static Long currentTenantId() {
        Long t = TenantContext.get();
        if (t != null) {
            return t;
        }
        UserPrincipal p = UserContext.get();
        return p == null ? null : p.tenantId();
    }

    private static String currentRequestId() {
        String rid = RequestContext.get();
        return rid == null ? "system" : rid;
    }

    private static Long currentOperatorId() {
        UserPrincipal p = UserContext.get();
        return p == null ? null : p.userId();
    }

    private String currentOperatorName() {
        UserPrincipal p = UserContext.get();
        if (p == null) {
            return null;
        }
        // 操作人列展示姓名（realName）而非账号。登录态已快照 realName（随会话还原），直接读内存、免查库。
        if (p.realName() != null && !p.realName().isBlank()) {
            return p.realName();
        }
        // 兜底：合成主体（导出 / 历史会话）principal 未带 realName 时按需查库；userId<=0（API 凭证）直接回退账号。
        if (p.userId() > 0) {
            String name = userQuery.realName(p.userId());
            if (name != null) {
                return name;
            }
        }
        return p.username();
    }

    private static Long currentOperatorDeptId() {
        UserPrincipal p = UserContext.get();
        return p == null ? null : p.mainDeptId();
    }
}
