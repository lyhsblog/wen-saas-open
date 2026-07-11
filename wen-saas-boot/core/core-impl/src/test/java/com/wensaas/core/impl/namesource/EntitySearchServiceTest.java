package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.MatchMode;
import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.namesource.NameSearch;
import com.wensaas.core.impl.namesource.EntitySearchService.EntityTypeMeta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntitySearchServiceTest {

    /**
     * 桩搜索源：在固定候选上**镜像 {@code LambdaNameSource} 的单关键字匹配语义**——关键字 OR 命中各字段，
     * primary 前缀（{@code likeRight}）、secondary 精确（{@code eq}）。{@code hasSecondary} 决定有无次字段
     * （驱动 hasCode / 第二个 searchField）。
     */
    static final class StubSearch implements NameSearch {
        private final String type;
        private final boolean tenant;
        private final boolean hasSecondary;
        private final boolean refTargetable;
        private final List<NameHit> hits;

        StubSearch(String type, boolean tenant, boolean hasSecondary, List<NameHit> hits) {
            this(type, tenant, hasSecondary, true, hits);
        }

        StubSearch(String type, boolean tenant, boolean hasSecondary, boolean refTargetable,
                   List<NameHit> hits) {
            this.type = type;
            this.tenant = tenant;
            this.hasSecondary = hasSecondary;
            this.refTargetable = refTargetable;
            this.hits = hits;
        }

        @Override public String entityType() { return type; }
        @Override public boolean tenantSearchable() { return tenant; }
        @Override public boolean referenceable() { return refTargetable; }

        @Override public List<FieldMeta> searchFields() {
            List<FieldMeta> f = new ArrayList<>();
            f.add(new FieldMeta("名称", MatchMode.PREFIX));
            if (hasSecondary) {
                f.add(new FieldMeta("编码", MatchMode.EXACT));
            }
            return f;
        }

        @Override public List<NameHit> search(SearchQuery q) {
            String kw = q.keyword() == null ? null : q.keyword().trim();
            if (kw == null || kw.isEmpty()) {
                return List.of();
            }
            List<NameHit> out = new ArrayList<>();
            for (NameHit h : hits) {
                boolean primaryHit = h.primary() != null && h.primary().startsWith(kw); // 前缀
                boolean secondaryHit = hasSecondary && kw.equals(h.secondary());          // 精确
                if (primaryHit || secondaryHit) {
                    out.add(h);
                }
            }
            return out;
        }
    }

    /** 桩 lookup：只提供 label。 */
    record StubLookup(String type, String label) implements NameLookup {
        @Override public String entityType() { return type; }
        @Override public String displayLabel() { return label; }
        @Override public Map<Long, String> batchLookup(Collection<Long> ids) { return Map.of(); }
    }

    @Test
    void tenantSearchableTypes_excludesNonTenant_andCarriesLabelAndHasCode() {
        EntitySearchService svc = new EntitySearchService(
                List.of(new StubSearch("sys.user", true, false, List.of()),
                        new StubSearch("sys.role", true, true, List.of()),
                        new StubSearch("sys.tenant", false, false, List.of())),
                List.of(new StubLookup("sys.user", "成员"),
                        new StubLookup("sys.role", "角色"),
                        new StubLookup("sys.tenant", "租户")));

        List<EntityTypeMeta> types = svc.tenantSearchableTypes();
        assertThat(types).extracting(EntityTypeMeta::code)
                .containsExactlyInAnyOrder("sys.user", "sys.role")
                .doesNotContain("sys.tenant");
        assertThat(types).filteredOn(t -> t.code().equals("sys.role"))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.label()).isEqualTo("角色");
                    assertThat(t.hasCode()).isTrue();          // 有次字段 → hasCode
                    assertThat(t.fields()).hasSize(2);
                });
        assertThat(types).filteredOn(t -> t.code().equals("sys.user"))
                .singleElement()
                .satisfies(t -> assertThat(t.hasCode()).isFalse()); // 仅 primary → 无 code
    }

    @Test
    void tenantSearch_rejectsNonTenantType_returnsEmpty_noLeak() {
        NameSearch.NameHit leak = new NameSearch.NameHit("1", "某租户", "某租户", null);
        EntitySearchService svc = new EntitySearchService(
                List.of(new StubSearch("sys.tenant", false, false, List.of(leak))),
                List.of(new StubLookup("sys.tenant", "租户")));

        // 前缀「某」本会命中「某租户」——门控须在调 search 前短路，证明不靠匹配为空而是越权拒绝
        assertThat(svc.tenantSearch("sys.tenant", "某", 10)).isEmpty();
    }

    @Test
    void tenantSearch_unknownType_returnsEmpty() {
        EntitySearchService svc = new EntitySearchService(List.of(), List.of());
        assertThat(svc.tenantSearch("no.such", "x", 10)).isEmpty();
    }

    @Test
    void tenantSearch_primaryMatchesPrefix_notSubstringNorOnlyExact() {
        NameSearch.NameHit zhangSan = new NameSearch.NameHit("7", "张三", "张三", null);
        NameSearch.NameHit zhangSi = new NameSearch.NameHit("8", "张四", "张四", null);
        NameSearch.NameHit liSi = new NameSearch.NameHit("9", "李四", "李四", null);
        EntitySearchService svc = new EntitySearchService(
                List.of(new StubSearch("sys.user", true, false, List.of(zhangSan, zhangSi, liSi))),
                List.of(new StubLookup("sys.user", "成员")));

        // 前缀「张」命中张三/张四，不及李四（likeRight 语义）
        assertThat(svc.tenantSearch("sys.user", "张", 10))
                .containsExactly(zhangSan, zhangSi);
        // 「三」是子串非前缀 → 不命中（证明已非 contains，更非旧的整串 eq）
        assertThat(svc.tenantSearch("sys.user", "三", 10)).isEmpty();
        // 整串是前缀的特例，仍命中
        assertThat(svc.tenantSearch("sys.user", "张三", 10)).containsExactly(zhangSan);
    }

    @Test
    void tenantSearch_singleKeyword_orsAcrossFields_codeStaysExact() {
        NameSearch.NameHit admin = new NameSearch.NameHit("1", "管理员", "管理员", "ADMIN");
        NameSearch.NameHit adminGrp = new NameSearch.NameHit("2", "管理组", "管理组", "ADMIN_GRP");
        EntitySearchService svc = new EntitySearchService(
                List.of(new StubSearch("sys.role", true, true, List.of(admin, adminGrp))),
                List.of(new StubLookup("sys.role", "角色")));

        // 关键字「ADMIN」：name 前缀不命中中文名，但次字段 code 精确命中 ADMIN（不前缀命中 ADMIN_GRP）
        assertThat(svc.tenantSearch("sys.role", "ADMIN", 10)).containsExactly(admin);
        // 关键字「管理」：name 前缀命中两者
        assertThat(svc.tenantSearch("sys.role", "管理", 10)).containsExactlyInAnyOrder(admin, adminGrp);
    }

    @Test
    void label_fallsBackToCode_whenNoMatchingLookup() {
        EntitySearchService svc = new EntitySearchService(
                List.of(new StubSearch("crm.customer", true, true, List.of())),
                List.of()); // 无对应 NameLookup

        assertThat(svc.tenantSearchableTypes()).singleElement()
                .satisfies(t -> assertThat(t.label()).isEqualTo("crm.customer"));
    }

    @Test
    void tenantReferenceableTypes_requiresSearchableAndReferenceable() {
        EntitySearchService svc = new EntitySearchService(
                List.of(new StubSearch("sys.user", true, false, true, List.of()),   // 数据实体：含
                        new StubSearch("sys.role", true, true, false, List.of()),    // 角色 referenceable=false：排除
                        new StubSearch("sys.tenant", false, false, true, List.of())), // platform-only：排除
                List.of(new StubLookup("sys.user", "成员"),
                        new StubLookup("sys.role", "角色"),
                        new StubLookup("sys.tenant", "租户")));

        assertThat(svc.tenantReferenceableTypes())
                .extracting(EntitySearchService.RefTypeMeta::code)
                .containsExactly("sys.user")
                .doesNotContain("sys.role", "sys.tenant");
        assertThat(svc.tenantReferenceableTypes()).singleElement()
                .satisfies(t -> assertThat(t.label()).isEqualTo("成员"));
    }

    @Test
    void isTenantReferenceable_perFlag() {
        EntitySearchService svc = new EntitySearchService(
                List.of(new StubSearch("sys.user", true, false, true, List.of()),
                        new StubSearch("sys.role", true, true, false, List.of()),
                        new StubSearch("sys.tenant", false, false, true, List.of())),
                List.of());

        assertThat(svc.isTenantReferenceable("sys.user")).isTrue();
        assertThat(svc.isTenantReferenceable("sys.role")).isFalse();   // referenceable=false
        assertThat(svc.isTenantReferenceable("sys.tenant")).isFalse(); // tenantSearchable=false
        assertThat(svc.isTenantReferenceable("no.such")).isFalse();    // 未注册
    }
}
