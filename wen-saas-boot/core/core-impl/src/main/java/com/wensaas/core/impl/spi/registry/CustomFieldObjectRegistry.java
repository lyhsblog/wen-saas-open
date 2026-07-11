package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import com.wensaas.core.impl.spi.module.NamespaceValidator;

/**
 * 自定义字段对象注册表。
 *
 * <p>v1 仅按 {@code code} 索引。v2 追加 {@code tableName.toLowerCase()} 反向索引，
 * 供 {@code CustomFieldQueryRewriter} 在拦截 SQL 后按物理表名反查 objectCode。
 *
 * <p>启动期同一 {@code tableName} 重复注册会抛 {@link BusinessModuleConflictException}（v1 已有
 * code 维度的命名空间校验由 {@link NamespaceValidator} 在 register 之前一次性完成；
 * tableName 维度的冲突走 register 这一层兜底，确保即便 NamespaceValidator 没扫到也不会沉默）。
 */
@Component
public class CustomFieldObjectRegistry extends AbstractRegistry<CustomFieldObjectDef> {

    private final Map<String, CustomFieldObjectDef> byTableName = new LinkedHashMap<>();

    public CustomFieldObjectRegistry() {
        super(CustomFieldObjectDef::code);
    }

    @Override
    public synchronized void register(String moduleCode, CustomFieldObjectDef def) {
        String tableKey = def.tableName().toLowerCase(Locale.ROOT);
        CustomFieldObjectDef existing = byTableName.get(tableKey);
        if (existing != null && !existing.code().equals(def.code())) {
            throw new BusinessModuleConflictException(
                    "CustomFieldObjectDef tableName 冲突: " + def.tableName()
                            + " 被 code=" + existing.code() + " 与 code=" + def.code() + " 同时占用");
        }
        super.register(moduleCode, def);
        byTableName.put(tableKey, def);
    }

    /** 按物理表名反查（大小写不敏感）。 */
    public synchronized Optional<CustomFieldObjectDef> findByTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byTableName.get(tableName.toLowerCase(Locale.ROOT)));
    }
}
