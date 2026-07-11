package com.wensaas.core.impl.customfield.interceptor;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.customfield.resolver.CustomFieldResolver;

class CustomFieldQueryRewriterTest {

    private static final String OBJECT_CODE = "cf.test_obj";
    private static final String TABLE_NAME = "cf_test_obj";

    private CustomFieldObjectRegistry registry;
    private CustomFieldResolver resolver;
    private CustomFieldQueryRewriter rewriter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = new CustomFieldObjectRegistry();
        registry.register("test", new CustomFieldObjectDef(
                OBJECT_CODE, TABLE_NAME, "extra", null));
        resolver = mock(CustomFieldResolver.class);
        ObjectProvider<CustomFieldResolver> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(resolver);
        rewriter = new CustomFieldQueryRewriter(registry, provider);
        TenantContext.set(9601L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void noCfPrefix_skipsRewriting() throws Exception {
        BoundSql bs = newBoundSql("SELECT id, name FROM cf_test_obj WHERE name = ?");
        rewriter.beforeQuery(null, anyMs(), null, null, null, bs);
        assertThat(bs.getSql()).isEqualTo("SELECT id, name FROM cf_test_obj WHERE name = ?");
    }

    @Test
    void tableNotRegistered_skipsRewriting() throws Exception {
        BoundSql bs = newBoundSql("SELECT * FROM crm_unknown WHERE cf_source = ?");
        rewriter.beforeQuery(null, anyMs(), null, null, null, bs);
        assertThat(bs.getSql()).isEqualTo("SELECT * FROM crm_unknown WHERE cf_source = ?");
    }

    @Test
    void indexedCfField_inWhere_isRewritten() throws Exception {
        when(resolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(def("cf_source", CustomFieldType.TEXT, true)));
        BoundSql bs = newBoundSql("SELECT id FROM cf_test_obj WHERE cf_source = ?");
        rewriter.beforeQuery(null, anyMs(), null, null, null, bs);
        assertThat(bs.getSql()).contains("extra->>'cf_source'");
        assertThat(bs.getSql()).doesNotContain("cf_test_obj WHERE cf_source =");
    }

    @Test
    void unindexedCfField_throwsUnindexed() {
        when(resolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(def("cf_source", CustomFieldType.TEXT, false)));
        BoundSql bs = newBoundSql("SELECT id FROM cf_test_obj WHERE cf_source = ?");
        assertThatThrownBy(() -> rewriter.beforeQuery(null, anyMs(), null, null, null, bs))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.UNINDEXED_QUERY_NOT_ALLOWED);
    }

    @Test
    void unregisteredCfField_throwsFieldNotFound() {
        when(resolver.resolveDefs(anyLong(), anyString())).thenReturn(List.of());
        BoundSql bs = newBoundSql("SELECT id FROM cf_test_obj WHERE cf_ghost = ?");
        assertThatThrownBy(() -> rewriter.beforeQuery(null, anyMs(), null, null, null, bs))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.FIELD_NOT_FOUND);
    }

    @Test
    void indexedCfField_inOrderBy_isRewritten() throws Exception {
        when(resolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(def("cf_score", CustomFieldType.NUMBER, true)));
        BoundSql bs = newBoundSql("SELECT id FROM cf_test_obj ORDER BY cf_score DESC");
        rewriter.beforeQuery(null, anyMs(), null, null, null, bs);
        assertThat(bs.getSql()).contains("extra->>'cf_score'");
    }

    @Test
    void noTenantContext_skipsRewriting() {
        TenantContext.clear();
        when(resolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(def("cf_source", CustomFieldType.TEXT, true)));
        BoundSql bs = newBoundSql("SELECT id FROM cf_test_obj WHERE cf_source = ?");
        assertThatCode(() -> rewriter.beforeQuery(null, anyMs(), null, null, null, bs))
                .doesNotThrowAnyException();
        assertThat(bs.getSql()).isEqualTo("SELECT id FROM cf_test_obj WHERE cf_source = ?");
    }

    private static CustomFieldDef def(String code, CustomFieldType type, boolean indexed) {
        return new CustomFieldDef(1L, 9601L, OBJECT_CODE, code, "F-" + code,
                type, false, indexed, null, null, null, 0);
    }

    private static MappedStatement anyMs() {
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getId()).thenReturn("any.id");
        return ms;
    }

    /** 直接通过反射 new BoundSql 实例（不依赖真 MyBatis Configuration）。 */
    private static BoundSql newBoundSql(String sql) {
        try {
            Constructor<BoundSql> c = BoundSql.class.getDeclaredConstructor(
                    org.apache.ibatis.session.Configuration.class,
                    String.class, java.util.List.class, Object.class);
            c.setAccessible(true);
            BoundSql bs = c.newInstance(new org.apache.ibatis.session.Configuration(),
                    sql, java.util.List.of(), new Object());
            return bs;
        } catch (Exception e) {
            // fallback: 反射改 sql 字段
            try {
                BoundSql bs = (BoundSql) sun.misc.Unsafe.class.getDeclaredField("THE_ONE").get(null);
                Field f = BoundSql.class.getDeclaredField("sql");
                f.setAccessible(true);
                f.set(bs, sql);
                return bs;
            } catch (Exception ee) {
                throw new RuntimeException("无法构造 BoundSql 用于测试", ee);
            }
        }
    }
}
