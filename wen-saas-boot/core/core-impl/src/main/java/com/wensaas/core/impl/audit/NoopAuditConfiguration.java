package com.wensaas.core.impl.audit;

import com.wensaas.core.api.audit.AuditLogRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.wensaas.core.impl.audit.pipeline.NoopAuditLogRecorder;

/**
 * {@code wen-saas-boot.audit.enabled=false} 时的兜底装配：仅 {@link NoopAuditLogRecorder}，
 * 让业务方调用点（{@code AuthServiceImpl} 等）始终能注入 {@link AuditLogRecorder}。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wen-saas-boot.audit", name = "enabled",
        havingValue = "false")
public class NoopAuditConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditLogRecorder.class)
    public AuditLogRecorder auditLogRecorder() {
        return new NoopAuditLogRecorder();
    }
}
