package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.ValueTransform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义逻辑转换 {@link ValueTransform} 的统一出口。
 *
 * <p>按 {@code type()} 聚合所有 {@link ValueTransform} bean；启动期校验：① 同 type 唯一；② 不与表基础引用
 * （{@link EntityNameResolver} 已注册的 {@code NameLookup} type）<b>撞 type</b>——同一 type 只能归一边，
 * 否则 {@code TransFiller} 分派语义歧义，fail-fast。
 *
 * <p>{@code TransFiller} 先查本注册表：命中走自定义转换（键值任意类型），未命中回落表引用。
 */
public class ValueTransformRegistry {

    private final Map<String, ValueTransform> byType;

    public ValueTransformRegistry(List<ValueTransform> transforms, EntityNameResolver nameResolver) {
        this.byType = index(transforms, nameResolver);
    }

    /** 取该 type 的自定义转换；未注册返回 {@code null}（调用方回落表引用）。 */
    public ValueTransform find(String type) {
        return type == null ? null : byType.get(type);
    }

    private static Map<String, ValueTransform> index(List<ValueTransform> transforms, EntityNameResolver nameResolver) {
        Map<String, ValueTransform> map = new HashMap<>();
        if (transforms == null) {
            return map;
        }
        for (ValueTransform t : transforms) {
            String type = t.type();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException(
                        "ValueTransform " + t.getClass().getName() + " 的 type 不能为空");
            }
            if (nameResolver != null && nameResolver.isRegistered(type)) {
                throw new IllegalStateException("转换 type 冲突: type=" + type + " 同时被 NameLookup（表引用）"
                        + "与 ValueTransform（" + t.getClass().getName() + "）注册，同一 type 只能归一边");
            }
            ValueTransform prev = map.put(type, t);
            if (prev != null && prev != t) {
                throw new IllegalStateException("ValueTransform 冲突: type=" + type
                        + " 被 " + prev.getClass().getName() + " 与 " + t.getClass().getName() + " 同时注册");
            }
        }
        return map;
    }
}
