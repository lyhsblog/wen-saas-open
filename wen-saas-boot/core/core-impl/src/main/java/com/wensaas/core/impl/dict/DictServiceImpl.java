package com.wensaas.core.impl.dict;

import com.wensaas.core.api.dict.DictGroup;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.tenant.TenantContext;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.wensaas.core.impl.dict.resolver.DictDataLoader;
import com.wensaas.core.impl.dict.resolver.DictResolver;

/**
 * {@link DictService} 缺省实现。纯读，所有路径走 {@link DictResolver}（带 Redis 缓存）。
 *
 * <p>租户上下文：从 {@link TenantContext#require()} 取 tenantId。未建立上下文则抛 IllegalStateException。
 */
public class DictServiceImpl implements DictService {

    private final DictResolver resolver;

    public DictServiceImpl(DictResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Optional<DictGroup> getGroup(String groupCode) {
        return resolver.resolve(TenantContext.require(), groupCode)
                .map(DictDataLoader.DictGroupSnapshot::group);
    }

    @Override
    public List<DictItem> listItems(String groupCode) {
        return resolver.resolve(TenantContext.require(), groupCode)
                .map(DictDataLoader.DictGroupSnapshot::items)
                .orElse(List.of());
    }

    @Override
    public Optional<DictItem> getItem(String groupCode, String itemCode) {
        return resolver.resolve(TenantContext.require(), groupCode)
                .flatMap(s -> findInTree(s.items(), itemCode));
    }

    @Override
    public Map<String, String> batchGetItemNames(String groupCode, Collection<String> itemCodes) {
        if (itemCodes == null || itemCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Optional<DictDataLoader.DictGroupSnapshot> snapshot = resolver.resolve(TenantContext.require(), groupCode);
        if (snapshot.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> wanted = new HashSet<>(itemCodes);
        Map<String, String> result = new LinkedHashMap<>();
        collectNames(snapshot.get().items(), wanted, result);
        return result;
    }

    private static Optional<DictItem> findInTree(List<DictItem> items, String code) {
        for (DictItem i : items) {
            if (i.code().equals(code)) {
                return Optional.of(i);
            }
            Optional<DictItem> sub = findInTree(i.children(), code);
            if (sub.isPresent()) {
                return sub;
            }
        }
        return Optional.empty();
    }

    private static void collectNames(List<DictItem> items, Set<String> wanted, Map<String, String> out) {
        for (DictItem i : items) {
            if (wanted.contains(i.code())) {
                out.put(i.code(), i.name());
            }
            if (!i.children().isEmpty()) {
                collectNames(i.children(), wanted, out);
            }
        }
    }
}
