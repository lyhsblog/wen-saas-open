package com.wensaas.core.impl.customfield.interceptor;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.wensaas.core.impl.customfield.resolver.CustomFieldResolver;

/**
 * MP {@link InnerInterceptor} v2 查询重写：把业务方 SQL 中 {@code cf_} 前缀的列引用
 * 自动翻译为 {@code extra->>'cf_xxx'}（PG jsonb 操作符），命中表达式索引。
 *
 * <p>三段式行为（v2 约束）：
 * <ul>
 *   <li>列在 def 中已索引化（{@code indexed=true}）→ 重写</li>
 *   <li>列在 def 中但未索引化 → 抛 {@link CustomFieldException.Code#UNINDEXED_QUERY_NOT_ALLOWED}</li>
 *   <li>列不在 def 中 → 抛 {@link CustomFieldException.Code#FIELD_NOT_FOUND}</li>
 * </ul>
 *
 * <p>覆盖位置：WHERE / HAVING / ORDER BY 表达式中的列引用；不动 SELECT 列表 / FROM JOIN ON 等。
 *
 * <p>不参与场景（直接放行不触发任何校验）：
 * <ul>
 *   <li>非 SELECT 语句（INSERT / UPDATE / DELETE）</li>
 *   <li>SQL 解析失败（罕见复杂语法）</li>
 *   <li>{@code FROM} 不是单表 / 表名未在 {@link CustomFieldObjectRegistry} 注册</li>
 *   <li>{@link TenantContext} 未设置（启动期 / 系统内部调用）</li>
 *   <li>业务方挂了 {@code @CrossTenantQuery}（按租户隔离也跳过的场景，cf 同步跳过）</li>
 * </ul>
 */
public class CustomFieldQueryRewriter implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CustomFieldQueryRewriter.class);

    private static final String FIELD_CODE_PREFIX = "cf_";

    private final CustomFieldObjectRegistry registry;
    private final ObjectProvider<CustomFieldResolver> resolverProvider;

    public CustomFieldQueryRewriter(CustomFieldObjectRegistry registry,
                                      ObjectProvider<CustomFieldResolver> resolverProvider) {
        this.registry = registry;
        this.resolverProvider = resolverProvider;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                              RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        if (InterceptorIgnoreHelper.willIgnoreTenantLine(ms.getId())) {
            return;
        }
        String sql = boundSql.getSql();
        if (sql == null || sql.isBlank()) {
            return;
        }
        if (!containsCfPrefix(sql)) {
            return; // 快路径：完全没 cf_ 出现就不解析
        }

        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            log.debug("[CustomField] SQL 解析失败，放行: {}", e.getMessage());
            return;
        }
        if (!(stmt instanceof PlainSelect plain)) {
            return;
        }
        if (!(plain.getFromItem() instanceof Table table)) {
            return;
        }

        String tableName = stripQuotes(table.getName());
        Optional<CustomFieldObjectDef> objectOpt = registry.findByTableName(tableName);
        if (objectOpt.isEmpty()) {
            return;
        }

        Long tenantId = currentTenantOrNull();
        if (tenantId == null) {
            return;
        }

        CustomFieldObjectDef object = objectOpt.get();
        CustomFieldResolver resolver = resolverProvider.getObject();
        List<CustomFieldDef> defs = resolver.resolveDefs(tenantId, object.code());
        Map<String, CustomFieldDef> defByCode = indexByCode(defs);

        RewriteVisitor visitor = new RewriteVisitor(defByCode, object.extraColumnName());
        if (plain.getWhere() != null) {
            plain.getWhere().accept(visitor, null);
        }
        if (plain.getHaving() != null) {
            plain.getHaving().accept(visitor, null);
        }
        if (plain.getOrderByElements() != null) {
            for (OrderByElement elem : plain.getOrderByElements()) {
                elem.getExpression().accept(visitor, null);
            }
        }
        if (plain.getGroupBy() != null) {
            ExpressionList<?> groupByList = plain.getGroupBy().getGroupByExpressionList();
            if (groupByList != null) {
                for (Object item : groupByList) {
                    if (item instanceof Expression expr) {
                        expr.accept(visitor, null);
                    }
                }
            }
        }

        if (visitor.rewrittenCount == 0) {
            return;
        }

        String newSql = plain.toString();
        writeBackSql(boundSql, newSql);
        log.debug("[CustomField] SQL 重写 table={} object={} rewrites={} new={}",
                tableName, object.code(), visitor.rewrittenCount, newSql);
    }

    private static boolean containsCfPrefix(String sql) {
        // 粗略检查：完整 SQL 字符串内出现 "cf_" 子串才进一步 parse
        return sql.indexOf(FIELD_CODE_PREFIX) >= 0;
    }

    private static Long currentTenantOrNull() {
        try {
            return TenantContext.require();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Map<String, CustomFieldDef> indexByCode(List<CustomFieldDef> defs) {
        Map<String, CustomFieldDef> map = new HashMap<>(defs.size() * 2);
        for (CustomFieldDef d : defs) {
            map.put(d.fieldCode(), d);
        }
        return map;
    }

    private static String stripQuotes(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }
        char c = s.charAt(0);
        if ((c == '"' || c == '`') && s.charAt(s.length() - 1) == c) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** 反射写回 BoundSql.sql（参照 MP PaginationInnerInterceptor）。 */
    private static void writeBackSql(BoundSql boundSql, String newSql) {
        try {
            Field f = BoundSql.class.getDeclaredField("sql");
            f.setAccessible(true);
            f.set(boundSql, newSql);
        } catch (Exception e) {
            throw new IllegalStateException("无法回写 BoundSql.sql", e);
        }
    }

    /** AST 访问者：找 Column → 校验 + 替换。 */
    private static final class RewriteVisitor extends ExpressionVisitorAdapter<Void> {

        private final Map<String, CustomFieldDef> defByCode;
        private final String extraColumn;
        int rewrittenCount = 0;

        RewriteVisitor(Map<String, CustomFieldDef> defByCode, String extraColumn) {
            this.defByCode = defByCode;
            this.extraColumn = extraColumn;
        }

        @Override
        public <S> Void visit(Column column, S context) {
            String name = stripQuotes(column.getColumnName());
            if (name == null || !name.startsWith(FIELD_CODE_PREFIX)) {
                return super.visit(column, context);
            }
            CustomFieldDef def = defByCode.get(name);
            if (def == null) {
                throw new CustomFieldException(CustomFieldException.Code.FIELD_NOT_FOUND,
                        "自定义字段未注册或已被软删: " + name + "（v2 约束：仅允许查询当前租户的已注册字段）");
            }
            if (!def.indexed()) {
                throw new CustomFieldException(CustomFieldException.Code.UNINDEXED_QUERY_NOT_ALLOWED,
                        "自定义字段 '" + name + "' 未索引化，不允许 WHERE/ORDER BY/GROUP BY/HAVING 查询；"
                                + "请在字段管理页打开索引化后再查（v2 约束）");
            }
            // 重写：替换 columnName 为 jsonb 操作符表达式；清空 table 前缀（避免 t.extra->>'cf_x' 这种坏拼）
            column.setColumnName(extraColumn + "->>'" + name + "'");
            column.setTable(null);
            rewrittenCount++;
            return super.visit(column, context);
        }
    }
}
