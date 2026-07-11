package com.wensaas.core.impl.permission.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.spi.def.DataPermissionTableDef;
import com.wensaas.core.impl.spi.registry.DataPermissionTableRegistry;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collection;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;
import com.wensaas.core.impl.permission.eval.EffectiveScope;
import com.wensaas.core.impl.permission.eval.PermissionResolver;

/**
 * MP {@link MultiDataPermissionHandler} 实现。对 SELECT FROM 中出现的、被
 * {@link DataPermissionTableRegistry} 注册的业务表注入 WHERE 条件。
 *
 * <p>触发条件（全部满足才过滤）：
 * <ol>
 *   <li>表已在 {@link DataPermissionTableRegistry} 注册</li>
 *   <li>{@link UserContext} 存在且非 tenantOwner、非开放 API 凭证（byApiKey；租户级系统对接，
 *       取租户内全量数据，等同 scope=ALL）</li>
 *   <li>{@link DataPermissionContext} 非空（当前请求有 {@code @DataPermission} 标注）</li>
 *   <li>若 {@code @DataPermission.tables} 非空，当前表须在声明列表内</li>
 * </ol>
 *
 * <p>scope 由 {@link PermissionResolver#scopeFor} 按 action 在「授予该权限点的角色」内解析（grant-coupled），
 * 部门集合已物化好：
 * <ul>
 *   <li>ALL → 不加条件（返回 null）</li>
 *   <li>SELF → {@code ownerColumn = userId}</li>
 *   <li>DEPT_AND_BELOW / DEPT_ONLY → {@code deptColumn IN (已物化部门)}；deptColumn 为空或部门集为空 → {@code 1=0}</li>
 * </ul>
 *
 * <p>列引用统一带表别名前缀（多表 join 时避免歧义）。
 */
public class DataPermissionHandlerImpl implements MultiDataPermissionHandler {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionHandlerImpl.class);

    private final DataPermissionTableRegistry registry;
    private final Supplier<PermissionResolver> resolverSupplier;

    public DataPermissionHandlerImpl(DataPermissionTableRegistry registry,
                                       ObjectProvider<PermissionResolver> resolverProvider) {
        this.registry = registry;
        this.resolverSupplier = resolverProvider::getObject;
    }

    /** 测试 / 内部使用：直接传 resolver。 */
    DataPermissionHandlerImpl(DataPermissionTableRegistry registry, PermissionResolver resolver) {
        this.registry = registry;
        this.resolverSupplier = () -> resolver;
    }

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        String rawName = stripQuotes(table.getName()).toLowerCase();
        Optional<DataPermissionTableDef> def = registry.findByKey(rawName);
        if (def.isEmpty()) {
            return null;
        }

        UserPrincipal principal = UserContext.get();
        if (principal == null) {
            return null;
        }
        if (principal.tenantOwner()) {
            return null;
        }
        // 开放 API 凭证（byApiKey）不做数据权限过滤：凭证定位是租户级系统对接，
        // 对接方需要租户内全量数据（scope=ALL 语义）；功能权限仍按凭证创建者身份
        // 走 @RequirePermission 检查，此处仅旁路行级过滤。
        if (principal.byApiKey()) {
            return null;
        }

        DataPermissionContext.Context ctx = DataPermissionContext.get();
        if (ctx == null) {
            return null; // 无 @DataPermission → 不过滤（名称解析 / lookup 天然豁免）
        }

        // 若声明了 tables，只过滤其中的表
        if (!ctx.tables().isEmpty() && !ctx.tables().contains(rawName)) {
            return null;
        }

        EffectiveScope eff = resolverSupplier.get().scopeFor(principal.tenantId(), principal.userId(), ctx.action());

        return switch (eff.scope()) {
            case ALL -> null;
            case SELF -> buildEqExpression(table, def.get().creatorColumn(), principal.userId(), mappedStatementId);
            case DEPT_AND_BELOW, DEPT_ONLY -> {
                if (def.get().deptColumn() == null) {
                    yield parseSafe("1 = 0", mappedStatementId);
                }
                if (eff.deptIds().isEmpty()) {
                    yield parseSafe("1 = 0", mappedStatementId);
                }
                yield buildInExpression(table, def.get().deptColumn(), eff.deptIds(), mappedStatementId);
            }
        };
    }

    private static Expression buildEqExpression(Table table, String column, long value, String msid) {
        return parseSafe(qualify(table, column) + " = " + value, msid);
    }

    private static Expression buildInExpression(Table table, String column,
                                                 Collection<Long> values, String msid) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        for (Long v : values) {
            joiner.add(String.valueOf(v));
        }
        return parseSafe(qualify(table, column) + " IN " + joiner, msid);
    }

    private static String qualify(Table table, String column) {
        String prefix = (table.getAlias() != null && table.getAlias().getName() != null)
                ? table.getAlias().getName()
                : stripQuotes(table.getName());
        return prefix + "." + column;
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

    private static Expression parseSafe(String sql, String msid) {
        try {
            return CCJSqlParserUtil.parseCondExpression(sql);
        } catch (JSQLParserException e) {
            log.error("数据权限片段解析失败 sql='{}' msid={}", sql, msid, e);
            throw new IllegalStateException("数据权限 SQL 片段解析失败: " + sql, e);
        }
    }
}
