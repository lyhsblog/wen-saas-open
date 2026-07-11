/**
 * 业务领域事件发布 SPI。
 *
 * <p>{@link com.wensaas.core.api.event.DomainEventPublisher} 让业务模块 / 底座模块以统一方式
 * 发布业务事件；默认实现走 RocketMQ topic {@code wen_event}，下游 Webhook Dispatcher Consumer
 * 按 {@code sys_webhook.subscribed_events} 匹配后扇出到各 {@code sys_webhook.target_url}。
 *
 * <p>事件命名约定：{@code "{module}.{verb}_{noun}"}，例如 {@code "tenant.opened"} /
 * {@code "crm.customer_created"}。
 */
package com.wensaas.core.api.event;
