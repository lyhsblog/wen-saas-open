package com.wensaas.core.impl.customfield.def;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.regex.Pattern;

/**
 * PostgreSQL 表达式索引 DDL 执行器。
 *
 * <p>对外只有 {@link #createIndex} / {@link #dropIndex} 两个方法。所有 SQL 走
 * {@link JdbcTemplate#execute(String)} 原生 DDL（不能用 PreparedStatement 占位符）。
 *
 * <p><b>SQL 注入防御</b>：三个标识符 {@code tableName} / {@code extraColumnName} / {@code fieldCode}
 * 全部按白名单 {@link #IDENTIFIER_PATTERN}（PostgreSQL 标识符规范：仅 {@code [a-z_][a-z0-9_]{0,62}}）
 * 校验。违反白名单的输入抛 {@link IllegalArgumentException}，DDL 不下发。
 *
 * <p>v1 不带 {@code CONCURRENTLY}：索引化切换被产品定义为"租户主动操作，建议低峰期"，
 * 同时 {@code CREATE INDEX CONCURRENTLY} 无法在事务内执行，与 {@link CustomFieldDefManager}
 * 的 {@code @Transactional} 边界冲突。
 */
public class CustomFieldIndexExecutor {

    private static final Logger log = LoggerFactory.getLogger(CustomFieldIndexExecutor.class);

    /** PG 标识符：仅小写字母 / 数字 / 下划线，首字母非数字，最长 63 字符。 */
    static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

    private final JdbcTemplate jdbc;

    public CustomFieldIndexExecutor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 为某 (table, extraColumn) 上的 fieldCode 创建表达式索引。
     * <pre>
     *   CREATE INDEX IF NOT EXISTS idx_{table}_extra_{field}
     *     ON {table} (tenant_id, ({extraColumn}-&gt;&gt;'{field}'));
     * </pre>
     */
    public void createIndex(String tableName, String extraColumnName, String fieldCode) {
        requireIdentifier("tableName", tableName);
        requireIdentifier("extraColumnName", extraColumnName);
        requireIdentifier("fieldCode", fieldCode);
        String indexName = indexName(tableName, fieldCode);
        String sql = "CREATE INDEX IF NOT EXISTS " + indexName
                + " ON " + tableName
                + " (tenant_id, (" + extraColumnName + "->>'" + fieldCode + "'))";
        log.info("[CustomField] 创建索引: {}", sql);
        jdbc.execute(sql);
    }

    /** 删除某 (table, fieldCode) 的表达式索引。 */
    public void dropIndex(String tableName, String fieldCode) {
        requireIdentifier("tableName", tableName);
        requireIdentifier("fieldCode", fieldCode);
        String indexName = indexName(tableName, fieldCode);
        String sql = "DROP INDEX IF EXISTS " + indexName;
        log.info("[CustomField] 删除索引: {}", sql);
        jdbc.execute(sql);
    }

    static String indexName(String tableName, String fieldCode) {
        return "idx_" + tableName + "_extra_" + fieldCode;
    }

    static void requireIdentifier(String paramName, String value) {
        if (value == null || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    paramName + " 非法 PG 标识符（需匹配 [a-z_][a-z0-9_]{0,62}）: " + value);
        }
    }
}
