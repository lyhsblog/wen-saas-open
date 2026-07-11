package com.wensaas.core.impl.auth.filter;

import com.wensaas.core.api.auth.permission.PublicEndpoint;
import com.wensaas.core.impl.auth.AuthProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 白名单匹配器：路径模式 + {@link PublicEndpoint} 注解联合判定。
 *
 * <p>路径模式来自 {@link AuthProperties#getPermitAll()}（Ant 通配）；注解在启动后由 HandlerMapping
 * 解析对应 HandlerMethod 决定。
 *
 * <p>注解判定需在请求时按实际 Handler 查询，避免启动时静态枚举。
 */
public class AuthPathMatcher implements SmartInitializingSingleton {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final List<String> permitAllPatterns;
    private final ApplicationContext applicationContext;
    private List<HandlerMapping> handlerMappings = Collections.emptyList();

    public AuthPathMatcher(AuthProperties props, ApplicationContext applicationContext) {
        List<String> patterns = props.getPermitAll();
        this.permitAllPatterns = patterns == null ? List.of() : List.copyOf(patterns);
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // 延迟到所有单例就绪后再取 HandlerMapping，避免构造期循环依赖
        this.handlerMappings = new ArrayList<>(
                applicationContext.getBeansOfType(HandlerMapping.class).values());
    }

    /** 该请求是否在白名单。 */
    public boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : permitAllPatterns) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return matchesPublicEndpoint(request);
    }

    private boolean matchesPublicEndpoint(HttpServletRequest request) {
        for (HandlerMapping hm : handlerMappings) {
            try {
                HandlerExecutionChain chain = hm.getHandler(request);
                if (chain == null) continue;
                Object handler = chain.getHandler();
                if (handler instanceof HandlerMethod hm0) {
                    if (hm0.getMethodAnnotation(PublicEndpoint.class) != null) return true;
                    if (hm0.getBeanType().isAnnotationPresent(PublicEndpoint.class)) return true;
                }
            } catch (Exception ignored) {
                // 不同 HandlerMapping 对不可解析请求会抛异常，忽略继续下一个
            }
        }
        return false;
    }
}
