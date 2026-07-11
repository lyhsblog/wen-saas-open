package com.wensaas.core.impl.job;

import com.wensaas.core.api.spi.ScheduledJob;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 每天 04:00 清理软删 90 天以上的自定义字段：
 * <ol>
 *   <li>扫 {@code sys_custom_field_def WHERE deleted_at &lt; now() - INTERVAL '90 days'}</li>
 *   <li>查 {@link CustomFieldObjectRegistry} 拿 {@code tableName} + {@code extraColumnName}</li>
 *   <li>{@code UPDATE {table} SET {extra} = {extra} - '{fieldCode}' WHERE tenant_id = ?}</li>
 *   <li>{@code DELETE FROM sys_custom_field_def WHERE id = ?}（真删，元数据无再保留意义）</li>
 * </ol>
 *
 * <p>所有标识符（tableName / extraColumnName / fieldCode）走白名单
 * {@link #IDENTIFIER_PATTERN}；违反抛错不下发 DDL/DML。
 *
 * <p>默认 cron：{@code 0 0 4 * * ?}。
 */
@Component
@ConditionalOnProperty(prefix = "wen-saas-boot.jobs", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CustomFieldSoftDeletedKeyCleanerJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(CustomFieldSoftDeletedKeyCleanerJob.class);

    /** PG 标识符：与 CustomFieldIndexExecutor 同惯例。 */
    static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

    private final JdbcTemplate jdbc;
    private final CustomFieldObjectRegistry objectRegistry;

    public CustomFieldSoftDeletedKeyCleanerJob(JdbcTemplate jdbc, CustomFieldObjectRegistry objectRegistry) {
        this.jdbc = jdbc;
        this.objectRegistry = objectRegistry;
    }

    @Override
    public String jobKey() {
        return "custom-field-soft-deleted-key-cleaner";
    }

    @Override
    public String cron() {
        return "0 0 4 * * ?";
    }

    /** 执行体（锁由调度框架统一加）；单测可直接调用。 */
    @Override
    public void execute() {
        int candidates = 0;
        int cleaned = 0;
        int skipped = 0;

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, tenant_id, object_code, field_code FROM sys_custom_field_def "
                        + "WHERE deleted_at IS NOT NULL AND deleted_at < now() - INTERVAL '90 days'");
        for (Map<String, Object> row : rows) {
            candidates++;
            long id = ((Number) row.get("id")).longValue();
            long tenantId = ((Number) row.get("tenant_id")).longValue();
            String objectCode = (String) row.get("object_code");
            String fieldCode = (String) row.get("field_code");
            try {
                Optional<CustomFieldObjectDef> def = objectRegistry.findByKey(objectCode);
                if (def.isEmpty()) {
                    log.warn("[CustomFieldKeyCleaner] 跳过 id={} objectCode={}：业务模块未注册或已下线",
                            id, objectCode);
                    skipped++;
                    continue;
                }
                String tableName = def.get().tableName();
                String extraColumn = def.get().extraColumnName();
                if (!IDENTIFIER_PATTERN.matcher(tableName).matches()
                        || !IDENTIFIER_PATTERN.matcher(extraColumn).matches()
                        || !IDENTIFIER_PATTERN.matcher(fieldCode).matches()) {
                    log.warn("[CustomFieldKeyCleaner] 跳过 id={}：标识符非法 table={} col={} field={}",
                            id, tableName, extraColumn, fieldCode);
                    skipped++;
                    continue;
                }
                String updateSql = "UPDATE " + tableName + " SET " + extraColumn
                        + " = " + extraColumn + " - ? WHERE tenant_id = ?";
                jdbc.update(updateSql, fieldCode, tenantId);
                jdbc.update("DELETE FROM sys_custom_field_def WHERE id = ?", id);
                cleaned++;
            } catch (RuntimeException e) {
                log.error("[CustomFieldKeyCleaner] 失败 id={} reason={}", id, e.getMessage(), e);
                skipped++;
            }
        }
        log.info("[CustomFieldKeyCleaner] 候选 {} 已清理 {} 跳过 {}", candidates, cleaned, skipped);
    }
}
