package com.wensaas.core.api.auth.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 Controller 类或方法为「公开端点」，{@code AuthenticationFilter} 不强制认证。
 *
 * <p>与配置 {@code wen-saas-boot.auth.permit-all} 并列。注解适合声明随代码走的固定公开接口
 * （如健康探针、登录、公开文档），配置适合运维灵活调整。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}
