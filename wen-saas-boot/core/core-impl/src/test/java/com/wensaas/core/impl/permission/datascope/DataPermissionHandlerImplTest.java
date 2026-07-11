package com.wensaas.core.impl.permission.datascope;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.spi.def.DataPermissionTableDef;
import com.wensaas.core.impl.spi.registry.DataPermissionTableRegistry;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.permission.eval.EffectiveScope;
import com.wensaas.core.impl.permission.eval.PermissionResolver;

class DataPermissionHandlerImplTest {

    private static final String ACTION = "crm_demo.customer.view";

    private DataPermissionTableRegistry registry;
    private PermissionResolver resolver;
    private DataPermissionHandlerImpl handler;

    @BeforeEach
    void setUp() {
        registry = new DataPermissionTableRegistry();
        registry.register("crm", new DataPermissionTableDef("crm.customer", "客户",
                "crm_customer", "created_by", "created_by_dept"));
        resolver = mock(PermissionResolver.class);
        handler = new DataPermissionHandlerImpl(registry, resolver);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        DataPermissionContext.clear();
    }

    // ===== 前置条件跳过（不应触发 scopeFor）=====

    @Test
    void unregisteredTable_returnsNull() {
        Expression expr = handler.getSqlSegment(new Table("unknown_table"), null, "msid");
        assertThat(expr).isNull();
        verify(resolver, never()).scopeFor(anyLong(), anyLong(), any());
    }

    @Test
    void userContextMissing_returnsNull() {
        DataPermissionContext.set(ACTION, new String[0]);
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNull();
        verify(resolver, never()).scopeFor(anyLong(), anyLong(), any());
    }

    @Test
    void tenantOwner_returnsNull() {
        UserContext.set(new UserPrincipal(99L, 1L, "owner", "s", true, null, false, null));
        DataPermissionContext.set(ACTION, new String[0]);
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNull();
        verify(resolver, never()).scopeFor(anyLong(), anyLong(), any());
    }

    @Test
    void byApiKey_returnsNull() {
        // 开放 API 凭证：租户级系统对接，数据权限不过滤（等同 scope=ALL），不触发 scopeFor
        UserContext.set(new UserPrincipal(7L, 1L, "api-key:ak_x", "api-key:1", false, null, true, 1L));
        DataPermissionContext.set(ACTION, new String[0]);
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNull();
        verify(resolver, never()).scopeFor(anyLong(), anyLong(), any());
    }

    @Test
    void noDataPermissionContext_returnsNull() {
        UserContext.set(new UserPrincipal(7L, 1L, "u", "s", false, null, false, null));
        // DataPermissionContext 未设置 → 不过滤（名称解析等天然豁免）
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNull();
        verify(resolver, never()).scopeFor(anyLong(), anyLong(), any());
    }

    // ===== scopeFor 返回的有效范围拼 WHERE（部门集合已物化）=====

    @Test
    void scopeAll_returnsNull() {
        prepare();
        when(resolver.scopeFor(1L, 7L, ACTION)).thenReturn(new EffectiveScope(DataScope.ALL, Set.of()));
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNull();
    }

    @Test
    void scopeSelf_generatesEq() {
        prepare();
        when(resolver.scopeFor(1L, 7L, ACTION)).thenReturn(new EffectiveScope(DataScope.SELF, Set.of()));
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).contains("created_by = 7");
    }

    @Test
    void scopeDeptAndBelow_generatesIn() {
        prepare();
        when(resolver.scopeFor(1L, 7L, ACTION))
                .thenReturn(new EffectiveScope(DataScope.DEPT_AND_BELOW, Set.of(10L, 20L, 30L)));
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNotNull();
        String s = expr.toString();
        assertThat(s).contains("created_by_dept IN");
        assertThat(s).contains("10").contains("20").contains("30");
    }

    @Test
    void scopeDept_emptyDeptIds_generates1eq0() {
        prepare();
        when(resolver.scopeFor(1L, 7L, ACTION)).thenReturn(new EffectiveScope(DataScope.DEPT_AND_BELOW, Set.of()));
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNotNull();
        assertThat(expr.toString().replace(" ", "")).isEqualTo("1=0");
    }

    @Test
    void scopeDeptOnly_usesDeptIds() {
        prepare();
        when(resolver.scopeFor(1L, 7L, ACTION)).thenReturn(new EffectiveScope(DataScope.DEPT_ONLY, Set.of(50L)));
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNotNull();
        String s = expr.toString();
        assertThat(s).contains("created_by_dept IN");
        assertThat(s).contains("50");
    }

    @Test
    void scopeDeptOnly_emptyDeptIds_generates1eq0() {
        prepare();
        when(resolver.scopeFor(1L, 7L, ACTION)).thenReturn(new EffectiveScope(DataScope.DEPT_ONLY, Set.of()));
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNotNull();
        assertThat(expr.toString().replace(" ", "")).isEqualTo("1=0");
    }

    // ===== tables 限定 =====

    @Test
    void tablesSpecified_currentTableIncluded_filters() {
        UserContext.set(new UserPrincipal(7L, 1L, "u", "s", false, null, false, null));
        DataPermissionContext.set(ACTION, new String[]{"crm_customer"});
        when(resolver.scopeFor(1L, 7L, ACTION)).thenReturn(new EffectiveScope(DataScope.SELF, Set.of()));
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).contains("created_by = 7");
    }

    @Test
    void tablesSpecified_currentTableExcluded_returnsNull() {
        UserContext.set(new UserPrincipal(7L, 1L, "u", "s", false, null, false, null));
        DataPermissionContext.set(ACTION, new String[]{"other_table"});
        // crm_customer 不在声明列表内 → 跳过
        Expression expr = handler.getSqlSegment(new Table("crm_customer"), null, "msid");
        assertThat(expr).isNull();
        verify(resolver, never()).scopeFor(anyLong(), anyLong(), any());
    }

    // ===== 其他边界 =====

    @Test
    void tableNameCaseInsensitive() {
        prepare();
        when(resolver.scopeFor(1L, 7L, ACTION)).thenReturn(new EffectiveScope(DataScope.SELF, Set.of()));
        Expression expr = handler.getSqlSegment(new Table("CRM_CUSTOMER"), null, "msid");
        assertThat(expr).isNotNull();
    }

    @Test
    void aliasUsedWhenPresent() {
        prepare();
        when(resolver.scopeFor(1L, 7L, ACTION)).thenReturn(new EffectiveScope(DataScope.SELF, Set.of()));
        Table t = new Table("crm_customer");
        t.setAlias(new net.sf.jsqlparser.expression.Alias("c", true));
        Expression expr = handler.getSqlSegment(t, null, "msid");
        assertThat(expr.toString()).contains("c.created_by = 7");
    }

    private static void prepare() {
        UserContext.set(new UserPrincipal(7L, 1L, "u", "s", false, null, false, null));
        DataPermissionContext.set(ACTION, new String[0]);
    }
}
