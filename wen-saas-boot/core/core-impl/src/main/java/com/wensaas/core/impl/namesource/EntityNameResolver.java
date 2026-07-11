package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.namesource.NameSearch;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体「id → 展示名」的统一出口。
 *
 * <p>按 {@code entityType} 聚合所有 {@link NameLookup} bean 分派；启动期校验同 entityType 唯一冲突。
 * 未登记的 entityType 走宽松策略（返回空 Map，调用方按 id 兜底），与 {@code NameLookup} 契约一致。
 *
 * <p>批量分派已消除逐条 N+1；跨请求缓存暂未启用（名称可变、避免脏读），后续按热点再加。
 */
public class EntityNameResolver {

    private final Map<String, NameLookup> byType;

    public EntityNameResolver(List<NameLookup> lookups) {
        this.byType = index(lookups);
    }

    /** 批量解析 entityType 下一批 id 的展示名；未登记 / 空入参返回空 Map。 */
    public Map<Long, String> resolve(String entityType, Collection<Long> ids) {
        if (entityType == null || ids == null || ids.isEmpty()) {
            return Map.of();
        }
        NameLookup lookup = byType.get(entityType);
        if (lookup == null) {
            return Map.of();
        }
        Map<Long, String> names = lookup.batchLookup(ids);
        return names == null ? Map.of() : names;
    }

    /** 解析单个 id 的展示名；未登记 / 无名返回 {@code null}。 */
    public String resolve(String entityType, Long id) {
        if (id == null) {
            return null;
        }
        return resolve(entityType, List.of(id)).get(id);
    }

    /** 实体类型的中文显示名（type-level）；未登记返回 {@code null}。 */
    public String displayLabel(String entityType) {
        NameLookup lookup = entityType == null ? null : byType.get(entityType);
        return lookup == null ? null : lookup.displayLabel();
    }

    /** 该 entityType 是否已注册 NameLookup（供审计 objectType 防漂移校验：合法值=已注册 code 或空）。 */
    public boolean isRegistered(String entityType) {
        return entityType != null && byType.containsKey(entityType);
    }

    /**
     * 该 entityType 的源是否「platform-only / 跨租户语义」（复用 {@link NameSearch#tenantSearchable()}=false 标志）。
     * 供 {@code TransFiller} 弱告警：这类源在响应回填中拿到空结果，疑似该字段漏设 {@code scope = CROSS}。
     */
    public boolean isPlatformOnly(String entityType) {
        NameLookup lookup = entityType == null ? null : byType.get(entityType);
        return lookup instanceof NameSearch ns && !ns.tenantSearchable();
    }

    private static Map<String, NameLookup> index(List<NameLookup> lookups) {
        Map<String, NameLookup> map = new HashMap<>();
        if (lookups == null) {
            return map;
        }
        for (NameLookup l : lookups) {
            String type = l.entityType();
            if (type == null || type.isBlank()) {
                throw new IllegalStateException(
                        "NameLookup " + l.getClass().getName() + " 的 entityType 不能为空");
            }
            NameLookup prev = map.put(type, l);
            if (prev != null && prev != l) {
                throw new IllegalStateException("NameLookup 冲突: entityType=" + type
                        + " 被 " + prev.getClass().getName() + " 与 " + l.getClass().getName() + " 同时注册");
            }
        }
        return map;
    }
}
