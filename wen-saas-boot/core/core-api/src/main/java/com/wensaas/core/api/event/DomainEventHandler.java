package com.wensaas.core.api.event;

/**
 * 进程内领域事件处理器：业务模块订阅平台/其他模块事件的实现接口。
 *
 * <p>接入：实现本接口注册为 Spring Bean，在 {@code BusinessModuleDescriptor.eventSubscriptions()}
 * 里声明 {@code EventSubscription(事件 code, bean 名)}。底座的订阅派发消费者监听统一事件
 * topic（{@code wen_event}），按事件 code 精确匹配订阅并逐个调用 handler。
 *
 * <p>执行语义：
 * <ul>
 *   <li>调用发生在事件租户的 {@code TenantContext} 内（{@code requestId} 也已还原）；</li>
 *   <li>每个 (handler, eventId) 经幂等守卫只执行一次——handler 正常返回即视为消费成功；</li>
 *   <li>抛出异常时该 handler 的幂等声明被释放、消息经 MQ 重投重做（其他已成功的 handler
 *       不会重复执行）；重试耗尽走 MQ 死信兜底。实现应自身幂等（以 {@code event.eventId()} 为准）；</li>
 *   <li>启动期校验：订阅声明的 bean 名必须存在且实现本接口，否则 fail-fast 阻断启动。</li>
 * </ul>
 */
public interface DomainEventHandler {

    void onEvent(EventEnvelope event);
}
