package com.wensaas.core.impl.webhook;

import com.wensaas.core.impl.file.CredentialCipher;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import com.wensaas.core.impl.webhook.dispatch.RocketMqWebhookSendPublisher;
import com.wensaas.core.impl.webhook.dispatch.WebhookDispatcherConsumer;
import com.wensaas.core.impl.webhook.dispatch.WebhookFailureTracker;
import com.wensaas.core.impl.webhook.dispatch.WebhookHttpClient;
import com.wensaas.core.impl.webhook.dispatch.WebhookSendPublisher;
import com.wensaas.core.impl.webhook.dispatch.WebhookSenderConsumer;
import com.wensaas.core.impl.webhook.dispatch.WebhookSignatureSigner;

/**
 * Webhook 推送框架装配。
 *
 * <p>{@code wen-saas-boot.webhook.enabled=true}（默认）时启用 dispatcher + sender 双 Consumer。
 * 关闭时只装配 Properties，业务方仍可调 {@code DomainEventPublisher} 发事件，但无 webhook 扇出。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebhookProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.webhook", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class WebhookConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebhookConfiguration.class);

    private final WebhookProperties props;

    public WebhookConfiguration(WebhookProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void logReady() {
        log.info("[Webhook] enabled, send-topic={}, retry-times={}, backoff={}",
                props.getSendTopic(), props.getRetryTimes(), props.getRetryBackoff());
    }

    @Bean
    public WebhookSignatureSigner webhookSignatureSigner() {
        return new WebhookSignatureSigner();
    }

    @Bean
    public WebhookHttpClient webhookHttpClient(WebhookProperties props) {
        return new WebhookHttpClient(props);
    }

    @Bean
    @ConditionalOnMissingBean(WebhookSendPublisher.class)
    public WebhookSendPublisher webhookSendPublisher(ObjectProvider<RocketMQClientTemplate> templateProvider,
                                                      WebhookProperties props) {
        return new RocketMqWebhookSendPublisher(templateProvider, props);
    }

    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public WebhookDispatcherConsumer webhookDispatcherConsumer(SysWebhookMapper webhookMapper,
                                                                WebhookSendPublisher publisher,
                                                                com.wensaas.core.impl.tenant.web.TenantActivityGuard activityGuard,
                                                                com.wensaas.core.impl.event.EventProperties eventProps,
                                                                com.wensaas.core.api.mq.MqConsumeSupport mqSupport) {
        return new WebhookDispatcherConsumer(webhookMapper, publisher, activityGuard, eventProps, mqSupport);
    }

    @Bean
    public WebhookFailureTracker webhookFailureTracker(SysWebhookMapper webhookMapper,
                                                       WebhookProperties props,
                                                       com.wensaas.core.api.notification.send.MessageService messageService,
                                                       com.wensaas.core.api.tenant.TenantQueryService tenantQuery) {
        return new WebhookFailureTracker(webhookMapper, props, messageService, tenantQuery);
    }

    @Bean
    public WebhookAdminService webhookAdminService(SysWebhookMapper webhookMapper,
                                                   SysWebhookLogMapper logMapper,
                                                   CredentialCipher cipher,
                                                   WebhookSendPublisher sendPublisher,
                                                   WebhookProperties props) {
        return new WebhookAdminService(webhookMapper, logMapper, cipher, sendPublisher, props);
    }

    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public WebhookSenderConsumer webhookSenderConsumer(SysWebhookMapper webhookMapper,
                                                        SysWebhookLogMapper logMapper,
                                                        WebhookHttpClient httpClient,
                                                        WebhookSignatureSigner signer,
                                                        CredentialCipher cipher,
                                                        WebhookSendPublisher publisher,
                                                        WebhookProperties props,
                                                        com.wensaas.core.impl.tenant.web.TenantActivityGuard activityGuard,
                                                        com.wensaas.core.api.idempotent.IdempotencyGuard idempotency,
                                                        com.wensaas.core.api.mq.MqConsumeSupport mqSupport,
                                                        WebhookFailureTracker failureTracker) {
        return new WebhookSenderConsumer(webhookMapper, logMapper, httpClient, signer,
                cipher, publisher, props, activityGuard, idempotency, mqSupport, failureTracker);
    }
}
