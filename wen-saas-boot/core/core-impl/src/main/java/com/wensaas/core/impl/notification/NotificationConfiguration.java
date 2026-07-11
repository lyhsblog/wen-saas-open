package com.wensaas.core.impl.notification;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.ChannelAdapter;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.NotificationPreferenceService;
import com.wensaas.core.api.notification.access.RelatedObjectAccessChecker;
import com.wensaas.core.api.auth.org.DeptQueryService;
import com.wensaas.core.api.auth.permission.UserRoleQueryService;
import com.wensaas.core.api.notification.inbox.MessageInboxService;
import com.wensaas.core.api.auth.user.UserDeliveryContactQuery;
import com.wensaas.core.impl.notification.adapter.EmailChannelAdapter;
import com.wensaas.core.impl.notification.adapter.ImChannelAdapters;
import com.wensaas.core.impl.notification.adapter.SmsChannelAdapter;
import com.wensaas.core.impl.notification.adapter.StationChannelAdapter;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.wensaas.core.impl.notification.access.RelatedObjectAccessEvaluator;
import com.wensaas.core.impl.notification.delivery.EmailNotificationConsumer;
import com.wensaas.core.impl.notification.delivery.ImNotificationConsumer;
import com.wensaas.core.impl.notification.delivery.MessageDeliveryRunner;
import com.wensaas.core.impl.notification.delivery.NotificationPublisher;
import com.wensaas.core.impl.notification.delivery.RecipientResolver;
import com.wensaas.core.impl.notification.delivery.RocketMqNotificationPublisher;
import com.wensaas.core.impl.notification.delivery.SmsNotificationConsumer;
import com.wensaas.core.impl.notification.delivery.StationNotificationConsumer;
import com.wensaas.core.impl.notification.delivery.SysMessageTaskMapper;
import com.wensaas.core.impl.notification.inbox.MessageInboxServiceImpl;
import com.wensaas.core.impl.notification.inbox.SysStationMessageMapper;
import com.wensaas.core.impl.notification.preference.NotificationPreferenceServiceImpl;
import com.wensaas.core.impl.notification.preference.SysUserNotificationPreferenceMapper;
import com.wensaas.core.impl.notification.template.NotificationTemplateCacheEvictor;
import com.wensaas.core.impl.notification.template.RedisTemplateResolver;
import com.wensaas.core.impl.notification.template.SysNotificationTemplateMapper;
import com.wensaas.core.impl.notification.template.SysNotificationTemplatePrefMapper;
import com.wensaas.core.impl.notification.template.TemplateDataLoader;
import com.wensaas.core.impl.notification.template.TemplateResolver;
import com.wensaas.core.impl.notification.template.TenantNotificationTemplateServiceImpl;

/**
 * 通知模块装配。
 *
 * <p>{@code wen-saas-boot.notification.enabled=true}（默认）时启用完整管道；
 * 关闭时由 sibling {@link NoopNotificationConfiguration} 提供 noop 兜底。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({NotificationProperties.class, SmsProperties.class})
@ConditionalOnProperty(prefix = "wen-saas-boot.notification", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class NotificationConfiguration {

    @Bean
    public TemplateDataLoader templateDataLoader(SysNotificationTemplateMapper mapper,
                                                 SysNotificationTemplatePrefMapper prefMapper) {
        return new TemplateDataLoader(mapper, prefMapper);
    }

    @Bean
    public TemplateResolver templateResolver(StringRedisTemplate redis,
                                              TemplateDataLoader loader,
                                              NotificationProperties props) {
        return new RedisTemplateResolver(redis, loader, props);
    }

    @Bean
    public RecipientResolver recipientResolver(UserDeliveryContactQuery deliveryContacts,
                                               UserRoleQueryService userRoleQuery, DeptQueryService deptQuery) {
        return new RecipientResolver(deliveryContacts, userRoleQuery, deptQuery);
    }

    @Bean
    public NotificationPublisher notificationPublisher(ObjectProvider<RocketMQClientTemplate> templateProvider,
                                                        NotificationProperties props) {
        return new RocketMqNotificationPublisher(templateProvider, props);
    }

    @Bean
    @ConditionalOnMissingBean(MessageService.class)
    public MessageService messageService(TemplateResolver templateResolver,
                                          RecipientResolver recipientResolver,
                                          NotificationPublisher publisher,
                                          SysMessageTaskMapper taskMapper) {
        return new MessageServiceImpl(templateResolver, recipientResolver, publisher, taskMapper);
    }

    @Bean
    @ConditionalOnMissingBean(MessageInboxService.class)
    public MessageInboxService messageInboxService(SysStationMessageMapper stationMapper,
                                                   SysMessageTaskMapper taskMapper) {
        return new MessageInboxServiceImpl(stationMapper, taskMapper);
    }

    @Bean
    public NotificationSpiSynchronizer notificationSpiSynchronizer(SysNotificationTemplateMapper mapper) {
        return new NotificationSpiSynchronizer(mapper);
    }

    @Bean
    public NotificationTemplateCacheEvictor notificationTemplateCacheEvictor(TemplateResolver templateResolver) {
        return new NotificationTemplateCacheEvictor(templateResolver);
    }

    @Bean
    public com.wensaas.core.api.notification.template.TenantNotificationTemplateService tenantNotificationTemplateService(
            SysNotificationTemplateMapper templateMapper,
            SysNotificationTemplatePrefMapper prefMapper,
            ObjectProvider<TemplateResolver> templateResolverProvider) {
        return new TenantNotificationTemplateServiceImpl(templateMapper, prefMapper, templateResolverProvider);
    }

    @Bean
    public NotificationPreferenceService notificationPreferenceService(
            SysUserNotificationPreferenceMapper mapper) {
        return new NotificationPreferenceServiceImpl(mapper);
    }

    @Bean
    public RelatedObjectAccessEvaluator relatedObjectAccessEvaluator(
            @Autowired(required = false) List<RelatedObjectAccessChecker> checkers) {
        return new RelatedObjectAccessEvaluator(checkers == null ? List.of() : checkers);
    }

    // ----- 通道适配器（业务方可通过自定义 Bean 覆盖；@ConditionalOnMissingBean 按 channel 区分） -----

    @Bean
    @ConditionalOnMissingBean(name = "stationChannelAdapter")
    public StationChannelAdapter stationChannelAdapter(SysStationMessageMapper mapper) {
        return new StationChannelAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "emailChannelAdapter")
    @ConditionalOnClass(JavaMailSender.class)
    public EmailChannelAdapter emailChannelAdapter(ObjectProvider<JavaMailSender> mailSender,
                                                     NotificationProperties props,
                                                     StringRedisTemplate redis) {
        JavaMailSender sender = mailSender.getIfAvailable();
        return new EmailChannelAdapter(sender, props.isEmailNoop() || sender == null,
                props.getEmailFrom(), redis, props.getEmailDailyLimit(),
                props.getEmailBlacklist());
    }

    @Bean
    @ConditionalOnMissingBean(name = "smsChannelAdapter")
    public SmsChannelAdapter smsChannelAdapter(SmsProperties smsProps) {
        return new SmsChannelAdapter(smsProps);
    }

    @Bean
    @ConditionalOnMissingBean(name = "wechatChannelAdapter")
    public ImChannelAdapters.WechatChannelAdapter wechatChannelAdapter() {
        return new ImChannelAdapters.WechatChannelAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(name = "wecomChannelAdapter")
    public ImChannelAdapters.WecomChannelAdapter wecomChannelAdapter() {
        return new ImChannelAdapters.WecomChannelAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(name = "dingtalkChannelAdapter")
    public ImChannelAdapters.DingtalkChannelAdapter dingtalkChannelAdapter() {
        return new ImChannelAdapters.DingtalkChannelAdapter();
    }

    @Bean
    public Map<Channel, ChannelAdapter> channelAdapterMap(List<ChannelAdapter> adapters) {
        Map<Channel, ChannelAdapter> map = new HashMap<>();
        for (ChannelAdapter a : adapters) {
            map.put(a.channel(), a);
        }
        return map;
    }

    @Bean
    public MessageDeliveryRunner messageDeliveryRunner(Map<Channel, ChannelAdapter> adapterMap,
                                                        SysMessageTaskMapper taskMapper,
                                                        UserDeliveryContactQuery deliveryContacts,
                                                        NotificationPublisher publisher,
                                                        NotificationProperties props,
                                                        com.wensaas.core.impl.tenant.web.TenantActivityGuard activityGuard,
                                                        NotificationPreferenceService preferenceService,
                                                        com.wensaas.core.api.idempotent.IdempotencyGuard idempotency) {
        return new MessageDeliveryRunner(adapterMap, taskMapper, deliveryContacts, publisher, props,
                activityGuard, preferenceService, idempotency);
    }

    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public StationNotificationConsumer stationNotificationConsumer(MessageDeliveryRunner runner,
                                                                   NotificationProperties props,
                                                                   com.wensaas.core.api.mq.MqConsumeSupport mqSupport) {
        return new StationNotificationConsumer(runner, props, mqSupport);
    }

    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public EmailNotificationConsumer emailNotificationConsumer(MessageDeliveryRunner runner,
                                                               NotificationProperties props,
                                                               com.wensaas.core.api.mq.MqConsumeSupport mqSupport) {
        return new EmailNotificationConsumer(runner, props, mqSupport);
    }

    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public SmsNotificationConsumer smsNotificationConsumer(MessageDeliveryRunner runner,
                                                           NotificationProperties props,
                                                           com.wensaas.core.api.mq.MqConsumeSupport mqSupport) {
        return new SmsNotificationConsumer(runner, props, mqSupport);
    }

    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public ImNotificationConsumer imNotificationConsumer(MessageDeliveryRunner runner,
                                                         NotificationProperties props,
                                                         com.wensaas.core.api.mq.MqConsumeSupport mqSupport) {
        return new ImNotificationConsumer(runner, props, mqSupport);
    }
}
