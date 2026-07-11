package com.wensaas.core.impl.audit.aspect;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

/**
 * 启动期校验所有 {@link Auditable#objectType()}：只能是**已注册实体 code**（{@code EntityTypes.*}）或**空串**，
 * 不合法即 fail-fast。防止后续 @Auditable 又塞中文/未注册 code 魔法值。
 *
 * <p>对象友好名由 {@code AuditObjects.add} / object_refs 承载、对象类型中文 label 由读取侧
 * {@code EntityNameResolver.displayLabel(code)} 派生，故 objectType 一律存 code（无对应实体则空）。
 */
public class AuditObjectTypeValidator implements SmartInitializingSingleton {

    private final ListableBeanFactory beanFactory;
    private final EntityNameResolver resolver;

    public AuditObjectTypeValidator(ListableBeanFactory beanFactory, EntityNameResolver resolver) {
        this.beanFactory = beanFactory;
        this.resolver = resolver;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String name : beanFactory.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = beanFactory.getBean(name);
            } catch (RuntimeException ignored) {
                continue; // 无法实例化（scoped / 异常）则跳过
            }
            Class<?> target = AopUtils.getTargetClass(bean);
            for (Method m : target.getMethods()) {
                Auditable a = AnnotatedElementUtils.findMergedAnnotation(m, Auditable.class);
                if (a == null) {
                    continue;
                }
                String objectType = a.objectType();
                if (objectType != null && !objectType.isBlank() && !resolver.isRegistered(objectType)) {
                    throw new IllegalStateException(
                            "@Auditable objectType 非法（需 EntityTypes 已注册 code 或空串）: \"" + objectType
                                    + "\" 于 " + target.getName() + "#" + m.getName()
                                    + "。对象类型须引用实体 code；无对应实体的操作请留空，由 action 承载语义。");
                }
            }
        }
    }
}
