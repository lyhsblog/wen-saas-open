package com.wensaas.core.impl.customfield.resolver;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldDisplayResolver;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.wensaas.core.impl.customfield.interceptor.CustomFieldEntitySupport;

/**
 * {@link CustomFieldDisplayResolver} 默认实现：把业务 entity / extra Map 中的自定义字段值，
 * 附加翻译后的 {@code __label}。
 *
 * <p>翻译规则：
 * <ul>
 *   <li>{@code ENUM} 字段：补 {@code cf_xxx__label = dictItemName}（从字典查；命中 RedisDictResolver 缓存）</li>
 *   <li>{@code REF} 字段：v3 起补 {@code cf_xxx__label = refDisplayName}（统一走
 *   {@link EntityNameResolver}：业务模块按 {@code objectCode} 注册 {@code NameLookup}；未注册则不翻译保留 ID）</li>
 *   <li>{@code TEXT / LONG_TEXT / NUMBER / DATE / BOOL}：值本身即显示值，不加 __label</li>
 * </ul>
 *
 * <p>字段显示名（{@code fieldName}）属 schema 元数据，前端通过 {@code customFieldService.listFields()}
 * 一次拉取即可，不进数据行。
 */
public class CustomFieldDisplayResolverImpl implements CustomFieldDisplayResolver {

    private final CustomFieldResolver customFieldResolver;
    private final ObjectProvider<DictService> dictServiceProvider;
    /** REF 字段统一走名称解析框架；按 refObjectCode 翻译。 */
    private final EntityNameResolver entityNameResolver;

    public CustomFieldDisplayResolverImpl(CustomFieldResolver customFieldResolver,
                                          ObjectProvider<DictService> dictServiceProvider,
                                          EntityNameResolver entityNameResolver) {
        this.customFieldResolver = customFieldResolver;
        this.dictServiceProvider = dictServiceProvider;
        this.entityNameResolver = entityNameResolver;
    }

    @Override
    public Optional<Map<String, Object>> resolveView(Object entity) {
        if (entity == null) {
            return Optional.empty();
        }
        Optional<CustomFieldEntitySupport.EntityMeta> metaOpt =
                CustomFieldEntitySupport.metaOf(entity.getClass());
        if (metaOpt.isEmpty()) {
            return Optional.empty();
        }
        CustomFieldEntitySupport.EntityMeta meta = metaOpt.get();
        Map<String, Object> extra = CustomFieldEntitySupport.readExtra(entity, meta);
        return Optional.of(resolveView(meta.objectCode(), extra));
    }

    @Override
    public Map<String, Object> resolveView(String objectCode, Map<String, Object> extra) {
        return resolveViews(objectCode, List.of(extra == null ? Map.of() : extra)).get(0);
    }

    @Override
    public List<Map<String, Object>> resolveViews(String objectCode, List<? extends Map<String, Object>> extras) {
        if (extras == null || extras.isEmpty()) {
            return List.of();
        }
        long tenantId = TenantContext.require();
        List<CustomFieldDef> defs = customFieldResolver.resolveDefs(tenantId, objectCode);
        Map<String, CustomFieldDef> defByCode = indexByCode(defs);
        DictService dict = dictServiceProvider.getIfAvailable();

        // 1) ENUM 字段：fieldCode → Set<itemCode>
        Map<String, Set<String>> enumCodesByField = new HashMap<>();
        // 2) REF 字段：fieldCode → Set<id>
        Map<String, Set<Long>> refIdsByField = new HashMap<>();

        for (Map<String, Object> extra : extras) {
            if (extra == null) continue;
            for (Map.Entry<String, Object> e : extra.entrySet()) {
                CustomFieldDef def = defByCode.get(e.getKey());
                if (def == null) continue;
                if (def.type() == CustomFieldType.ENUM && dict != null && e.getValue() instanceof String code) {
                    enumCodesByField.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(code);
                } else if (def.type() == CustomFieldType.REF && def.refObjectCode() != null) {
                    Long refId = refLongOf(e.getValue());
                    if (refId != null) {
                        refIdsByField.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(refId);
                    }
                }
            }
        }

        // 3) 单次查每个 ENUM 字段对应字典分组的 itemName 集合
        Map<String, Map<String, String>> enumNamesByField = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : enumCodesByField.entrySet()) {
            CustomFieldDef def = defByCode.get(entry.getKey());
            enumNamesByField.put(entry.getKey(), dict.batchGetItemNames(def.refDictCode(), entry.getValue()));
        }

        // 4) 单次查每个 REF 字段对应业务对象的 displayName（统一走名称解析框架；未登记 entityType 返回空）
        Map<String, Map<Long, String>> refNamesByField = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : refIdsByField.entrySet()) {
            CustomFieldDef def = defByCode.get(entry.getKey());
            refNamesByField.put(entry.getKey(),
                    entityNameResolver.resolve(def.refObjectCode(), entry.getValue()));
        }

        // 5) 按行拼视图
        List<Map<String, Object>> views = new ArrayList<>(extras.size());
        for (Map<String, Object> extra : extras) {
            Map<String, Object> view = new LinkedHashMap<>();
            if (extra == null) {
                views.add(view);
                continue;
            }
            view.putAll(extra);
            for (Map.Entry<String, Object> e : extra.entrySet()) {
                Map<String, String> enumNames = enumNamesByField.get(e.getKey());
                if (enumNames != null && e.getValue() instanceof String code) {
                    String name = enumNames.get(code);
                    if (name != null) {
                        view.put(e.getKey() + LABEL_SUFFIX, name);
                    }
                    continue;
                }
                Map<Long, String> refNames = refNamesByField.get(e.getKey());
                if (refNames != null) {
                    Long refId = refLongOf(e.getValue());
                    if (refId != null) {
                        String name = refNames.get(refId);
                        if (name != null) {
                            view.put(e.getKey() + LABEL_SUFFIX, name);
                        }
                    }
                }
            }
            views.add(view);
        }
        return views;
    }

    /** REF 值取 Long：接受 Number（非浮点）或纯数字 String（雪花 ID 前端用字符串避免精度丢失）。 */
    private static Long refLongOf(Object v) {
        if (v instanceof Number n && !(n instanceof Double) && !(n instanceof Float)) {
            return n.longValue();
        }
        if (v instanceof String s && s.matches("\\d{1,19}")) {
            return Long.parseLong(s);
        }
        return null;
    }

    private static Map<String, CustomFieldDef> indexByCode(List<CustomFieldDef> defs) {
        Map<String, CustomFieldDef> map = new HashMap<>(defs.size() * 2);
        for (CustomFieldDef d : defs) {
            map.put(d.fieldCode(), d);
        }
        return map;
    }
}
