package com.wensaas.core.impl.customfield.resolver;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldDisplayResolver;
import com.wensaas.core.api.customfield.CustomFieldExtension;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.customfield.interceptor.CustomFieldEntitySupport;

class CustomFieldDisplayResolverTest {

    private CustomFieldResolver customFieldResolver;
    private DictService dictService;
    private ObjectProvider<DictService> dictProvider;

    @CustomFieldExtension(objectCode = "crm.customer")
    static class Customer {
        Map<String, Object> extra;
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        CustomFieldEntitySupport.clearCache();
        customFieldResolver = mock(CustomFieldResolver.class);
        dictService = mock(DictService.class);
        dictProvider = mock(ObjectProvider.class);
        when(dictProvider.getIfAvailable()).thenReturn(dictService);
        TenantContext.set(9601L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CustomFieldDisplayResolver build(List<NameLookup> lookups) {
        return new CustomFieldDisplayResolverImpl(customFieldResolver, dictProvider, new EntityNameResolver(lookups));
    }

    @Test
    void enumField_addsLabel() {
        CustomFieldDisplayResolver displayResolver = build(List.of());
        when(customFieldResolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(
                        defEnum("cf_source", "GENDER"),
                        def("cf_score", CustomFieldType.NUMBER)));
        when(dictService.batchGetItemNames(eq("GENDER"), any()))
                .thenReturn(Map.of("WEB", "官网"));

        Map<String, Object> view = displayResolver.resolveView("crm.customer",
                Map.of("cf_source", "WEB", "cf_score", 85));

        assertThat(view).containsEntry("cf_source", "WEB");
        assertThat(view).containsEntry("cf_source__label", "官网");
        assertThat(view).containsEntry("cf_score", 85);
        assertThat(view).doesNotContainKey("cf_score__label");
    }

    @Test
    void refField_noLookupRegistered_notTranslated() {
        CustomFieldDisplayResolver displayResolver = build(List.of());
        when(customFieldResolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(defRef("cf_related", "crm.customer")));

        Map<String, Object> view = displayResolver.resolveView("crm.customer",
                Map.of("cf_related", 12345L));

        assertThat(view).containsEntry("cf_related", 12345L);
        assertThat(view).doesNotContainKey("cf_related__label");
    }

    @Test
    void refField_withLookup_addsLabel() {
        NameLookup lookup = new StubLookup("crm.customer", Map.of(
                12345L, "客户 A",
                23456L, "客户 B"));
        CustomFieldDisplayResolver displayResolver = build(List.of(lookup));
        when(customFieldResolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(defRef("cf_related", "crm.customer")));

        Map<String, Object> view = displayResolver.resolveView("ops.lead",
                Map.of("cf_related", 12345L));

        assertThat(view).containsEntry("cf_related", 12345L);
        assertThat(view).containsEntry("cf_related__label", "客户 A");
    }

    @Test
    void refField_idNotInLookupResult_preservedNoLabel() {
        NameLookup lookup = new StubLookup("crm.customer", Map.of(99999L, "存在的"));
        CustomFieldDisplayResolver displayResolver = build(List.of(lookup));
        when(customFieldResolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(defRef("cf_related", "crm.customer")));

        Map<String, Object> view = displayResolver.resolveView("ops.lead",
                Map.of("cf_related", 12345L));

        assertThat(view).containsEntry("cf_related", 12345L);
        assertThat(view).doesNotContainKey("cf_related__label"); // 已删除场景
    }

    @Test
    void refField_batch_singleLookupCall() {
        StubLookup lookup = new StubLookup("crm.customer", Map.of(
                1L, "Alice", 2L, "Bob", 3L, "Charlie"));
        CustomFieldDisplayResolver displayResolver = build(List.of(lookup));
        when(customFieldResolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(defRef("cf_related", "crm.customer")));

        List<Map<String, Object>> views = displayResolver.resolveViews("ops.lead", List.of(
                Map.of("cf_related", 1L),
                Map.of("cf_related", 2L),
                Map.of("cf_related", 1L),
                Map.of("cf_related", 3L)));

        assertThat(views.get(0)).containsEntry("cf_related__label", "Alice");
        assertThat(views.get(1)).containsEntry("cf_related__label", "Bob");
        assertThat(views.get(2)).containsEntry("cf_related__label", "Alice");
        assertThat(views.get(3)).containsEntry("cf_related__label", "Charlie");
        // 批量优化：仅 1 次 lookup 调用，去重后 ID 集合 = {1, 2, 3}
        assertThat(lookup.callCount).isEqualTo(1);
        assertThat(lookup.lastIds).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void refField_unknownRefObjectCode_notTranslated() {
        NameLookup lookup = new StubLookup("crm.customer", Map.of(1L, "A"));
        CustomFieldDisplayResolver displayResolver = build(List.of(lookup));
        when(customFieldResolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(defRef("cf_related", "no.such.object"))); // refObjectCode 没注册 lookup

        Map<String, Object> view = displayResolver.resolveView("ops.lead",
                Map.of("cf_related", 1L));

        assertThat(view).doesNotContainKey("cf_related__label");
    }

    @Test
    void undeclaredKey_preserved_noLabel() {
        CustomFieldDisplayResolver displayResolver = build(List.of());
        when(customFieldResolver.resolveDefs(anyLong(), anyString())).thenReturn(List.of());
        Map<String, Object> view = displayResolver.resolveView("crm.customer",
                Map.of("cf_unknown", "X"));
        assertThat(view).containsEntry("cf_unknown", "X");
        assertThat(view).doesNotContainKey("cf_unknown__label");
    }

    @Test
    void entityShortcut_picksUpObjectCode() {
        CustomFieldDisplayResolver displayResolver = build(List.of());
        when(customFieldResolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(defEnum("cf_source", "GENDER")));
        when(dictService.batchGetItemNames(eq("GENDER"), any()))
                .thenReturn(Map.of("WEB", "官网"));

        Customer c = new Customer();
        c.extra = new HashMap<>(Map.of("cf_source", "WEB"));

        Optional<Map<String, Object>> view = displayResolver.resolveView(c);
        assertThat(view).isPresent();
        assertThat(view.get()).containsEntry("cf_source__label", "官网");
    }

    @Test
    void batchView_singleDictLookupPerField() {
        CustomFieldDisplayResolver displayResolver = build(List.of());
        when(customFieldResolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(defEnum("cf_source", "GENDER")));
        when(dictService.batchGetItemNames(eq("GENDER"), any()))
                .thenReturn(Map.of("WEB", "官网", "APP", "移动 APP"));

        List<Map<String, Object>> views = displayResolver.resolveViews("crm.customer", List.of(
                Map.of("cf_source", "WEB"),
                Map.of("cf_source", "APP"),
                Map.of("cf_source", "WEB")));

        assertThat(views).hasSize(3);
        assertThat(views.get(0)).containsEntry("cf_source__label", "官网");
        assertThat(views.get(1)).containsEntry("cf_source__label", "移动 APP");
        assertThat(views.get(2)).containsEntry("cf_source__label", "官网");
        verify(dictService, times(1)).batchGetItemNames(eq("GENDER"), any());
    }

    @Test
    void duplicateLookupForSameObjectCode_throwsAtConstruction() {
        NameLookup l1 = new StubLookup("crm.customer", Map.of());
        NameLookup l2 = new StubLookup("crm.customer", Map.of());
        assertThatThrownBy(() -> build(List.of(l1, l2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("crm.customer");
    }

    @Test
    void lookupWithBlankObjectCode_throwsAtConstruction() {
        NameLookup l = new StubLookup("", Map.of());
        assertThatThrownBy(() -> build(List.of(l)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("entityType 不能为空");
    }

    private static CustomFieldDef def(String code, CustomFieldType type) {
        return new CustomFieldDef(1L, 9601L, "crm.customer", code, "F-" + code,
                type, false, false, null, null, null, 0);
    }

    private static CustomFieldDef defEnum(String code, String refDictCode) {
        return new CustomFieldDef(1L, 9601L, "crm.customer", code, "F-" + code,
                CustomFieldType.ENUM, false, false, null, refDictCode, null, 0);
    }

    private static CustomFieldDef defRef(String code, String refObjectCode) {
        return new CustomFieldDef(1L, 9601L, "crm.customer", code, "F-" + code,
                CustomFieldType.REF, false, false, null, null, refObjectCode, 0);
    }

    /** 测试用 lookup：记录调用次数 + 最后一次的 ID 集合。 */
    static final class StubLookup implements NameLookup {
        private final String entityType;
        private final Map<Long, String> table;
        int callCount = 0;
        Collection<Long> lastIds;

        StubLookup(String entityType, Map<Long, String> table) {
            this.entityType = entityType;
            this.table = table;
        }

        @Override
        public String entityType() {
            return entityType;
        }

        @Override
        public String displayLabel() {
            return entityType;
        }

        @Override
        public Map<Long, String> batchLookup(Collection<Long> ids) {
            callCount++;
            lastIds = ids;
            Map<Long, String> result = new HashMap<>();
            for (Long id : ids) {
                String name = table.get(id);
                if (name != null) {
                    result.put(id, name);
                }
            }
            return result;
        }
    }
}
