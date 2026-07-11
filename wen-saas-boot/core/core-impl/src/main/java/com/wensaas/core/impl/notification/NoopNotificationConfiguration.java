package com.wensaas.core.impl.notification;

import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.inbox.DeliveryDetail;
import com.wensaas.core.api.notification.inbox.InboxPage;
import com.wensaas.core.api.notification.inbox.MessageDetailView;
import com.wensaas.core.api.notification.inbox.MessageInboxService;
import com.wensaas.core.api.notification.inbox.UnreadSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * {@code wen-saas-boot.notification.enabled=false} 时的兜底装配：仅注入 {@link NoopMessageService}。
 *
 * <p>与 {@code NoopAuditConfiguration} 同模式：业务方注入 {@link MessageService} 不会失败，
 * 仅 send 时无效果。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wen-saas-boot.notification", name = "enabled",
        havingValue = "false")
public class NoopNotificationConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageService.class)
    public MessageService messageService() {
        return new NoopMessageService();
    }

    @Bean
    @ConditionalOnMissingBean(MessageInboxService.class)
    public MessageInboxService messageInboxService() {
        return new NoopMessageInboxService();
    }

    /** notification 禁用时：收件箱呈现为空、未读 0；写操作静默 no-op。 */
    static final class NoopMessageInboxService implements MessageInboxService {
        @Override public InboxPage list(String category, String readState, String keyword,
                                        boolean archived, int page, int size) {
            return new InboxPage(List.of(), 0, page, size);
        }
        @Override public MessageDetailView open(Long id) { return new MessageDetailView(); }
        @Override public void markRead(Long id) { }
        @Override public void markAllRead(String category) { }
        @Override public void delete(Long id) { }
        @Override public UnreadSummary unreadSummary() { return new UnreadSummary(0, List.of()); }
        @Override public List<DeliveryDetail> deliveries(Long id) { return List.of(); }
    }
}
