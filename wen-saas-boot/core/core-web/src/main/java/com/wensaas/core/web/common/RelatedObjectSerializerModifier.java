package com.wensaas.core.web.common;

import com.wensaas.core.api.notification.access.RelatedObjectGuarded;
import com.wensaas.core.impl.notification.access.RelatedObjectAccessEvaluator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.ValueSerializerModifier;

/**
 * Jackson 序列化器修饰器：对带 {@link RelatedObjectGuarded} 的 bean，用
 * {@link RelatedObjectGuardSerializer} 包裹其默认 bean 序列化器，植入关联对象权限探针。
 *
 * <p>由 {@link JsonConfig} 通过 {@code SimpleModule.setSerializerModifier} 注册。
 */
final class RelatedObjectSerializerModifier extends ValueSerializerModifier {

    private final transient RelatedObjectAccessEvaluator evaluator;

    RelatedObjectSerializerModifier(RelatedObjectAccessEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ValueSerializer<?> modifySerializer(SerializationConfig config,
                                               BeanDescription.Supplier beanDescRef,
                                               ValueSerializer<?> serializer) {
        Class<?> raw = beanDescRef.getBeanClass();
        if (!raw.isAnnotationPresent(RelatedObjectGuarded.class)) {
            return serializer;
        }
        return new RelatedObjectGuardSerializer((ValueSerializer<Object>) serializer, evaluator);
    }
}
