package com.wensaas.core.web.common;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.notification.access.RelatedObjectBody;
import com.wensaas.core.api.notification.access.RelatedObjectGuarded;
import com.wensaas.core.api.notification.access.RelatedObjectId;
import com.wensaas.core.api.notification.access.RelatedObjectLink;
import com.wensaas.core.api.notification.access.RelatedObjectType;
import com.wensaas.core.impl.notification.access.RelatedObjectAccessEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 包裹带 {@link RelatedObjectGuarded} 的 DTO 的 bean 序列化器，做关联对象权限探针。
 *
 * <p>序列化时读取 {@code @RelatedObjectType}/{@code @RelatedObjectId} 字段 → 调
 * {@link RelatedObjectAccessEvaluator}：有权原样委派；无权则生成<b>降级副本</b>（
 * {@code @RelatedObjectBody} 置为 {@link RelatedObjectGuarded#maskedBodyText()}、
 * {@code @RelatedObjectLink} 置 null，其余字段保留）再委派——不原地改对象，避免污染共享引用。
 *
 * <p>探针抛异常时 <b>fail-closed</b>（按无权降级）。被标注 DTO 必须有无参构造且字段可写
 * （非 record），否则降级副本生成失败抛 {@link IllegalStateException}。
 */
final class RelatedObjectGuardSerializer extends ValueSerializer<Object> {

    private static final Logger log = LoggerFactory.getLogger(RelatedObjectGuardSerializer.class);

    private static final Map<Class<?>, GuardMeta> META_CACHE = new ConcurrentHashMap<>();

    private final ValueSerializer<Object> delegate;
    private final RelatedObjectAccessEvaluator evaluator;

    RelatedObjectGuardSerializer(ValueSerializer<Object> delegate, RelatedObjectAccessEvaluator evaluator) {
        this.delegate = delegate;
        this.evaluator = evaluator;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctx) {
        GuardMeta meta = META_CACHE.computeIfAbsent(value.getClass(), GuardMeta::of);

        boolean allowed;
        try {
            allowed = evaluator.canAccess(meta.readType(value), meta.readId(value), currentUserId());
        } catch (RuntimeException e) {
            log.warn("relatedObject 权限探针异常，按无权降级处理 type={}", value.getClass().getName(), e);
            allowed = false;
        }

        if (allowed) {
            delegate.serialize(value, gen, ctx);
            return;
        }
        delegate.serialize(meta.maskedCopy(value), gen, ctx);
    }

    private static Long currentUserId() {
        UserPrincipal p = UserContext.get();
        return p == null ? null : p.userId();
    }

    /** 一个 guarded 类的字段元数据，按 Class 缓存。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    private static class GuardMeta {
        private Field typeField;
        private Field idField;
        private Field bodyField;
        private Field linkField;
        private List<Field> allFields;
        private String maskedBodyText;

        static GuardMeta of(Class<?> clazz) {
            Field typeField = null;
            Field idField = null;
            Field bodyField = null;
            Field linkField = null;
            List<Field> all = new ArrayList<>();
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.isSynthetic() || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    f.setAccessible(true);
                    all.add(f);
                    if (f.isAnnotationPresent(RelatedObjectType.class)) typeField = f;
                    if (f.isAnnotationPresent(RelatedObjectId.class)) idField = f;
                    if (f.isAnnotationPresent(RelatedObjectBody.class)) bodyField = f;
                    if (f.isAnnotationPresent(RelatedObjectLink.class)) linkField = f;
                }
            }
            RelatedObjectGuarded ann = clazz.getAnnotation(RelatedObjectGuarded.class);
            String masked = ann == null ? "该消息关联的内容已不可见" : ann.maskedBodyText();
            return new GuardMeta(typeField, idField, bodyField, linkField, all, masked);
        }

        String readType(Object value) {
            return typeField == null ? null : (String) readField(typeField, value);
        }

        String readId(Object value) {
            return idField == null ? null : (String) readField(idField, value);
        }

        /** 生成降级副本：body=占位、link=null，其余字段原样复制。 */
        Object maskedCopy(Object value) {
            try {
                var ctor = value.getClass().getDeclaredConstructor();
                ctor.setAccessible(true);
                Object copy = ctor.newInstance();
                for (Field f : allFields) {
                    f.set(copy, f.get(value));
                }
                if (bodyField != null) bodyField.set(copy, maskedBodyText);
                if (linkField != null) linkField.set(copy, null);
                return copy;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("@RelatedObjectGuarded DTO 必须有无参构造且字段可写（非 record）: "
                        + value.getClass().getName(), e);
            }
        }

        private static Object readField(Field f, Object value) {
            try {
                return f.get(value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("读取 relatedObject 字段失败: " + f, e);
            }
        }
    }
}
