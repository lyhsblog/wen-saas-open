package com.wensaas.core.impl.spi.module;

import com.wensaas.core.api.spi.ModuleMetadata;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 已注册业务模块的中心注册表。
 */
@Component
public class BusinessModuleRegistry {

    private final Map<String, ModuleMetadata> byCode = new LinkedHashMap<>();

    public synchronized void register(ModuleMetadata metadata) {
        byCode.put(metadata.code(), metadata);
    }

    public synchronized List<ModuleMetadata> getAll() {
        return List.copyOf(byCode.values());
    }

    public synchronized Optional<ModuleMetadata> find(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public synchronized int size() {
        return byCode.size();
    }
}
