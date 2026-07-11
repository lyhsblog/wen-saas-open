package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.EventSubscription;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件订阅注册表。
 *
 * <p>不参与命名空间唯一性校验：多个模块可以订阅同一 topic。
 * 内部用 List 保留贡献顺序，按模块编码索引便于查询。
 */
@Component
public class EventSubscriptionRegistry {

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, List<Entry>> byModule = new LinkedHashMap<>();
    private final Map<String, List<Entry>> byTopic = new LinkedHashMap<>();

    public synchronized void register(String moduleCode, EventSubscription subscription) {
        Entry entry = new Entry(moduleCode, subscription);
        entries.add(entry);
        byModule.computeIfAbsent(moduleCode, k -> new ArrayList<>()).add(entry);
        byTopic.computeIfAbsent(subscription.topic(), k -> new ArrayList<>()).add(entry);
    }

    public synchronized List<Entry> getAll() {
        return List.copyOf(entries);
    }

    public synchronized List<Entry> findByTopic(String topic) {
        return List.copyOf(byTopic.getOrDefault(topic, List.of()));
    }

    public synchronized int size() {
        return entries.size();
    }

    public record Entry(String moduleCode, EventSubscription subscription) {
    }
}
