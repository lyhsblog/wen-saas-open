package com.wensaas.core.impl.audit;

import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.audit.SensitiveHandler;
import com.wensaas.core.api.audit.Sensitives;
import jakarta.annotation.PostConstruct;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import com.wensaas.core.impl.audit.aspect.AuditObjectTypeValidator;
import com.wensaas.core.impl.audit.aspect.AuditableAspect;
import com.wensaas.core.impl.audit.log.SysLoginLogMapper;
import com.wensaas.core.impl.audit.log.SysOperationLogMapper;
import com.wensaas.core.impl.audit.pending.PendingAuditFlushFilter;
import com.wensaas.core.impl.audit.pipeline.AuditEventConsumer;
import com.wensaas.core.impl.audit.pipeline.AuditEventPublisher;
import com.wensaas.core.impl.audit.pipeline.AuditLogRecorderImpl;
import com.wensaas.core.impl.audit.pipeline.LoginAuditBatchBuffer;
import com.wensaas.core.impl.audit.pipeline.LoginAuditEventConsumer;
import com.wensaas.core.impl.audit.pipeline.OperationAuditBatchBuffer;
import com.wensaas.core.impl.audit.pipeline.RocketMqAuditEventPublisher;

/**
 * 操作日志框架装配。
 *
 * <p>{@code wen-saas-boot.audit.enabled=true}（默认）时启用完整管道；关闭时由 sibling
 * {@link NoopAuditConfiguration} 提供 noop 兜底，业务方代码不变。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.audit", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AuditConfiguration {

    private final List<SensitiveHandler> handlers;

    public AuditConfiguration(List<SensitiveHandler> handlers) {
        this.handlers = handlers;
    }

    @PostConstruct
    public void registerSensitiveHandlers() {
        for (SensitiveHandler h : handlers) {
            Sensitives.registerHandler(h);
        }
    }

    @Bean
    public AuditEventPublisher auditEventPublisher(RocketMQClientTemplate template, AuditProperties props) {
        return new RocketMqAuditEventPublisher(template, props);
    }

    @Bean
    @ConditionalOnMissingBean(AuditLogRecorder.class)
    public AuditLogRecorder auditLogRecorder(AuditEventPublisher publisher) {
        return new AuditLogRecorderImpl(publisher);
    }

    @Bean
    public AuditableAspect auditableAspect(AuditLogRecorder recorder,
                                           com.wensaas.core.api.auth.user.UserQueryService userQuery) {
        return new AuditableAspect(recorder, userQuery);
    }

    /** 启动期校验 @Auditable objectType ∈ 已注册实体 code ∪ 空串，防魔法值漂移。 */
    @Bean
    public AuditObjectTypeValidator auditObjectTypeValidator(
            org.springframework.beans.factory.ListableBeanFactory beanFactory,
            com.wensaas.core.impl.namesource.EntityNameResolver resolver) {
        return new AuditObjectTypeValidator(beanFactory, resolver);
    }

    /** 审计失败事件的 HTTP 状态回填 filter（响应提交后读 response.getStatus()）。 */
    @Bean
    public PendingAuditFlushFilter pendingAuditFlushFilter(AuditLogRecorder recorder) {
        return new PendingAuditFlushFilter(recorder);
    }

    @Bean
    public OperationAuditBatchBuffer operationAuditBatchBuffer(SysOperationLogMapper mapper,
                                                                 AuditProperties props,
                                                                 tools.jackson.databind.ObjectMapper jackson) {
        return new OperationAuditBatchBuffer(mapper, props, jackson);
    }

    @Bean
    public LoginAuditBatchBuffer loginAuditBatchBuffer(SysLoginLogMapper mapper,
                                                         AuditProperties props) {
        return new LoginAuditBatchBuffer(mapper, props);
    }

    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public AuditEventConsumer auditEventConsumer(OperationAuditBatchBuffer buffer,
                                                  com.wensaas.core.impl.tenant.web.TenantActivityGuard activityGuard,
                                                  AuditProperties props,
                                                  com.wensaas.core.api.mq.MqConsumeSupport mqSupport) {
        return new AuditEventConsumer(buffer, activityGuard, props, mqSupport);
    }

    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public LoginAuditEventConsumer loginAuditEventConsumer(LoginAuditBatchBuffer buffer,
                                                            com.wensaas.core.impl.tenant.web.TenantActivityGuard activityGuard,
                                                            AuditProperties props,
                                                            com.wensaas.core.api.mq.MqConsumeSupport mqSupport) {
        return new LoginAuditEventConsumer(buffer, activityGuard, props, mqSupport);
    }
}
