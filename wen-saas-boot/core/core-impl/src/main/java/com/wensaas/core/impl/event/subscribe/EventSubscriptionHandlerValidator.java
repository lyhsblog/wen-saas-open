package com.wensaas.core.impl.event.subscribe;

import com.wensaas.core.api.event.DomainEventHandler;
import com.wensaas.core.impl.spi.registry.EventSubscriptionRegistry;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * 启动期校验所有 {@code eventSubscriptions()} 声明：handlerBeanName 必须存在且实现
 * {@link DomainEventHandler}，否则 fail-fast——防止订阅静默失效（bean 名拼错 / 忘注册），
 * 消费期才发现就丢事件了。
 */
public class EventSubscriptionHandlerValidator implements SmartInitializingSingleton {

    private final EventSubscriptionRegistry registry;
    private final ListableBeanFactory beanFactory;

    public EventSubscriptionHandlerValidator(EventSubscriptionRegistry registry,
                                             ListableBeanFactory beanFactory) {
        this.registry = registry;
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (EventSubscriptionRegistry.Entry entry : registry.getAll()) {
            String beanName = entry.subscription().handlerBeanName();
            Object bean;
            try {
                bean = beanFactory.getBean(beanName);
            } catch (NoSuchBeanDefinitionException e) {
                throw new IllegalStateException("模块 " + entry.moduleCode() + " 订阅事件 "
                        + entry.subscription().topic() + " 声明的处理器 Bean \"" + beanName
                        + "\" 不存在。请注册同名 Bean 并实现 DomainEventHandler。");
            }
            if (!(bean instanceof DomainEventHandler)) {
                throw new IllegalStateException("模块 " + entry.moduleCode() + " 订阅事件 "
                        + entry.subscription().topic() + " 的处理器 Bean \"" + beanName
                        + "\"（" + bean.getClass().getName() + "）未实现 DomainEventHandler。");
            }
        }
    }
}
