package com.wensaas.core.api.audit;

/**
 * 自定义脱敏 SPI。业务方提供 {@link org.springframework.stereotype.Component} Bean，
 * {@link Sensitives} 在启动期 / 首次调用收集后按 {@link #name()} 索引。
 */
public interface SensitiveHandler {

    /** 与 {@link Sensitive#handler()} 匹配的 bean name；不能为空。 */
    String name();

    /** 入参原值；返回脱敏后的值（任意可 JSON 序列化对象）。原值为 null 时仍调用方决定行为。 */
    Object mask(Object raw);
}
