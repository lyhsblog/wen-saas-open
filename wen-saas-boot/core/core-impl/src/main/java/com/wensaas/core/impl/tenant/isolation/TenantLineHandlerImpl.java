package com.wensaas.core.impl.tenant.isolation;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.wensaas.core.api.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;
import com.wensaas.core.impl.tenant.TenantProperties;

/**
 * 多租户 SQL 改写所需的 {@link TenantLineHandler} 实现。
 *
 * <p>规则：
 * <ul>
 *   <li>{@link #getTenantId()} 从 {@link TenantContext} 读当前 tenant_id；未设置则抛 IllegalStateException</li>
 *   <li>{@link #ignoreTable(String)} 在 {@code platform-only-tables} 白名单中返回 true（跳过注入）</li>
 *   <li>{@link #getTenantIdColumn()} 固定 {@code tenant_id}</li>
 * </ul>
 */
@Component
public class TenantLineHandlerImpl implements TenantLineHandler {

    private final TenantProperties properties;

    public TenantLineHandlerImpl(TenantProperties properties) {
        this.properties = properties;
    }

    @Override
    public Expression getTenantId() {
        return new LongValue(TenantContext.require());
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (tableName == null) {
            return false;
        }
        return properties.getPlatformOnlyTables().contains(tableName.toLowerCase());
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }
}
