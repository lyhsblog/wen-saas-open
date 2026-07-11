package com.wensaas.core.impl.customfield.interceptor;

import com.wensaas.core.api.customfield.CustomFieldExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务 entity 上 {@link CustomFieldExtension} 注解的反射工具。
 *
 * <p>核心契约：
 * <ul>
 *   <li>类级 {@code @CustomFieldExtension(objectCode="...", extraField="extra")}</li>
 *   <li>必须有一个 {@code Map<String,Object> {extraField}} 字段（不强制 getter/setter，反射 Field 直读直写）</li>
 *   <li>反射结果按 Class 缓存；非自定义 entity 缓存为 {@link EntityMeta#ABSENT} 避免重复扫描</li>
 * </ul>
 */
public final class CustomFieldEntitySupport {

    private static final Logger log = LoggerFactory.getLogger(CustomFieldEntitySupport.class);

    private static final ConcurrentHashMap<Class<?>, EntityMeta> CACHE = new ConcurrentHashMap<>();

    private CustomFieldEntitySupport() {
    }

    /** 取 entity 类的自定义字段元数据；无注解返回 {@link Optional#empty()}。 */
    public static Optional<EntityMeta> metaOf(Class<?> clazz) {
        if (clazz == null) {
            return Optional.empty();
        }
        EntityMeta cached = CACHE.computeIfAbsent(clazz, CustomFieldEntitySupport::resolve);
        return cached == EntityMeta.ABSENT ? Optional.empty() : Optional.of(cached);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readExtra(Object entity, EntityMeta meta) {
        try {
            return (Map<String, Object>) meta.extraField().get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("无法读取 entity extra 字段: " + entity.getClass().getName(), e);
        }
    }

    public static void writeExtra(Object entity, EntityMeta meta, Map<String, Object> value) {
        try {
            meta.extraField().set(entity, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("无法写入 entity extra 字段: " + entity.getClass().getName(), e);
        }
    }

    /** 仅供测试使用。 */
    public static void clearCache() {
        CACHE.clear();
    }

    private static EntityMeta resolve(Class<?> clazz) {
        CustomFieldExtension ann = clazz.getAnnotation(CustomFieldExtension.class);
        if (ann == null) {
            return EntityMeta.ABSENT;
        }
        String fieldName = ann.extraField();
        Field field = findField(clazz, fieldName);
        if (field == null) {
            throw new IllegalStateException("@CustomFieldExtension 标注的 entity " + clazz.getName()
                    + " 缺少字段 '" + fieldName + "'（应为 Map<String,Object> 类型）");
        }
        if (!Map.class.isAssignableFrom(field.getType())) {
            throw new IllegalStateException("@CustomFieldExtension 标注的 entity " + clazz.getName()
                    + " 字段 '" + fieldName + "' 类型不是 Map（实际 " + field.getType().getName() + "）");
        }
        field.setAccessible(true);
        log.debug("[CustomField] 注册 entity {} → objectCode={} extraField={}",
                clazz.getName(), ann.objectCode(), fieldName);
        return new EntityMeta(ann.objectCode(), field);
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /** entity 类的自定义字段元数据。 */
    public record EntityMeta(String objectCode, Field extraField) {

        /** 哨兵值：表示该类不带 @CustomFieldExtension 注解。 */
        static final EntityMeta ABSENT = new EntityMeta("", null);
    }
}
