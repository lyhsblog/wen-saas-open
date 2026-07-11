package com.wensaas.core.impl.spi.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 扩展点注册表基类。线程安全的简易内存实现。
 *
 * @param <T> 扩展点定义类型
 */
public abstract class AbstractRegistry<T> {

    private final Map<String, T> byKey = new LinkedHashMap<>();
    private final Map<String, String> moduleByKey = new LinkedHashMap<>();
    private final Function<T, String> keyFn;

    protected AbstractRegistry(Function<T, String> keyFn) {
        this.keyFn = keyFn;
    }

    public synchronized void register(String moduleCode, T def) {
        String key = keyFn.apply(def);
        byKey.put(key, def);
        moduleByKey.put(key, moduleCode);
    }

    public synchronized List<T> getAll() {
        return List.copyOf(byKey.values());
    }

    public synchronized Optional<T> findByKey(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public synchronized Optional<String> findOwnerModule(String key) {
        return Optional.ofNullable(moduleByKey.get(key));
    }

    /**
     * 统计某业务模块贡献的扩展点数量。供运营「业务模块」管理页内省聚合（X8）使用。
     */
    public synchronized int countByModule(String moduleCode) {
        int n = 0;
        for (String owner : moduleByKey.values()) {
            if (owner.equals(moduleCode)) {
                n++;
            }
        }
        return n;
    }

    public synchronized int size() {
        return byKey.size();
    }
}
