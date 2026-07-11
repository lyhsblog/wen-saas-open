package com.wensaas.core.api.spi.def;

/**
 * 平台事件订阅贡献项：把「事件 code → 本模块的处理器 Bean」登记给底座的订阅派发消费者。
 *
 * <p>处理器须实现 {@code com.wensaas.core.api.event.DomainEventHandler}（启动期校验，
 * 缺 Bean / 类型不符 fail-fast）；执行语义（租户上下文、幂等、重试）见该接口 javadoc。
 *
 * @param topic 订阅的事件 code，<b>精确匹配</b> {@code EventEnvelope.eventType}
 *              （如 {@code "sys.member_disabled"}、{@code "crm_demo.customer_created"}；
 *              所有事件走同一 MQ topic，这里的 topic 指业务事件类型而非 MQ topic 名）
 * @param handlerBeanName 处理器 Spring Bean 名
 */
public record EventSubscription(String topic, String handlerBeanName) {
}
