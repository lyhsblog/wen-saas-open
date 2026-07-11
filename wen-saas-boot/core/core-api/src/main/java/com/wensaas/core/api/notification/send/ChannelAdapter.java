package com.wensaas.core.api.notification.send;

import com.wensaas.core.api.notification.template.RenderedMessage;
/**
 * 通道适配器 SPI。按通道一个实现，底座通过 {@link Channel} 路由。
 *
 * <p>实现要求：
 * <ul>
 *   <li>{@link #canDeliver} 判断"该收件人在该通道是否可触达"（如 SMS 需手机号），不可触达返回 false</li>
 *   <li>{@link #deliver} 同步执行；适配器内不再做重试，重试由 Consumer 层统一</li>
 *   <li>抛异常 → Consumer 视为 FAILED 并按指数退避重试</li>
 * </ul>
 */
public interface ChannelAdapter {

    Channel channel();

    boolean canDeliver(Recipient recipient);

    DeliveryResult deliver(RenderedMessage message, Recipient recipient);
}
