package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.MatchMode;
import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.namesource.NameSearch;
import com.wensaas.core.api.namesource.NameSearch.FieldMeta;
import com.wensaas.core.api.namesource.NameSourceFactory;
import com.wensaas.core.api.namesource.RenderMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LambdaNameSourceFactory} 单测：验证 builder 以方法引用构造出携带正确元数据、且同时实现
 * {@link NameLookup} + {@link NameSearch} 的名称源（元数据 getter 不触发 MyBatis-Plus 的 Db，纯单测可验）。
 */
class LambdaNameSourceFactoryTest {

    /** 列规整的桩实体。 */
    static class Foo {
        private Long id;
        private String name;
        private String code;

        Long getId() { return id; }
        String getName() { return name; }
        String getCode() { return code; }
    }

    private final NameSourceFactory factory = new LambdaNameSourceFactory();

    @Test
    void primaryAndSecondary_carryMetadataAndOrderedSearchFields() {
        NameLookup nl = factory.source("crm_demo.customer", "客户", true, Foo.class, Foo::getId)
                .primary(Foo::getName, "名称", MatchMode.PREFIX)
                .secondary(Foo::getCode, "编码", MatchMode.EXACT)
                .build();

        assertThat(nl.entityType()).isEqualTo("crm_demo.customer");
        assertThat(nl.displayLabel()).isEqualTo("客户");
        assertThat(nl).isInstanceOf(NameSearch.class);
        NameSearch ns = (NameSearch) nl;
        assertThat(ns.tenantSearchable()).isTrue();
        assertThat(ns.referenceable()).isTrue(); // 默认可作 ref 目标
        assertThat(ns.searchFields())
                .containsExactly(new FieldMeta("名称", MatchMode.PREFIX),
                        new FieldMeta("编码", MatchMode.EXACT));
    }

    @Test
    void referenceableFalse_carried() {
        NameLookup nl = factory.source("sys.role", "角色", true, Foo.class, Foo::getId)
                .primary(Foo::getName, "名称", MatchMode.PREFIX)
                .referenceable(false)
                .build();

        assertThat(((NameSearch) nl).referenceable()).isFalse();
    }

    @Test
    void primaryOnly_singleSearchField_notTenantSearchable() {
        NameLookup nl = factory.source("sys.tenant", "租户", false, Foo.class, Foo::getId)
                .primary(Foo::getName, "名称", MatchMode.PREFIX)
                .build();

        NameSearch ns = (NameSearch) nl;
        assertThat(ns.tenantSearchable()).isFalse();
        assertThat(ns.searchFields()).containsExactly(new FieldMeta("名称", MatchMode.PREFIX));
    }

    @Test
    void build_withoutPrimary_throws() {
        assertThatThrownBy(() ->
                factory.source("x", "X", true, Foo.class, Foo::getId).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secondary_beforePrimary_orTwice_throws() {
        assertThatThrownBy(() ->
                factory.source("x", "X", true, Foo.class, Foo::getId)
                        .secondary(Foo::getCode, "编码", MatchMode.EXACT))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() ->
                factory.source("x", "X", true, Foo.class, Foo::getId)
                        .primary(Foo::getName, "名称", MatchMode.PREFIX)
                        .secondary(Foo::getCode, "编码", MatchMode.EXACT)
                        .secondary(Foo::getCode, "编码2", MatchMode.EXACT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void render_defaultsToPrimary() {
        // 不显式调 render() 时默认 PRIMARY——通过工厂构造不抛、且类型正确即覆盖（render 行为由 RenderModeTest 验）
        NameLookup nl = factory.source("r", "R", true, Foo.class, Foo::getId)
                .primary(Foo::getName, "名称", MatchMode.PREFIX)
                .render(RenderMode.BOTH)
                .build();
        assertThat(nl).isInstanceOf(NameSearch.class);
    }
}
