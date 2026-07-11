package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.NameLookup;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityNameResolverTest {

    /** 简单桩：按固定表翻译，记录调用次数。 */
    static final class StubLookup implements NameLookup {
        private final String type;
        private final Map<Long, String> table;
        int calls = 0;

        StubLookup(String type, Map<Long, String> table) {
            this.type = type;
            this.table = table;
        }

        @Override
        public String entityType() {
            return type;
        }

        @Override
        public String displayLabel() {
            return type;
        }

        @Override
        public Map<Long, String> batchLookup(Collection<Long> ids) {
            calls++;
            Map<Long, String> out = new java.util.HashMap<>();
            for (Long id : ids) {
                String n = table.get(id);
                if (n != null) {
                    out.put(id, n);
                }
            }
            return out;
        }
    }

    @Test
    void dispatchesByEntityType() {
        EntityNameResolver resolver = new EntityNameResolver(List.of(
                new StubLookup("sys.tenant", Map.of(1L, "租户甲", 2L, "租户乙")),
                new StubLookup("sys.user", Map.of(1L, "张三"))));

        assertThat(resolver.resolve("sys.tenant", List.of(1L, 2L)))
                .containsEntry(1L, "租户甲").containsEntry(2L, "租户乙");
        assertThat(resolver.resolve("sys.user", 1L)).isEqualTo("张三");
    }

    @Test
    void unknownEntityType_returnsEmpty_notThrow() {
        EntityNameResolver resolver = new EntityNameResolver(List.of(
                new StubLookup("sys.tenant", Map.of(1L, "租户甲"))));

        assertThat(resolver.resolve("no.such", List.of(1L))).isEmpty();
        assertThat(resolver.resolve("no.such", 1L)).isNull();
    }

    @Test
    void missingId_absentFromMap() {
        EntityNameResolver resolver = new EntityNameResolver(List.of(
                new StubLookup("sys.tenant", Map.of(1L, "租户甲"))));

        Map<Long, String> out = resolver.resolve("sys.tenant", List.of(1L, 999L));
        assertThat(out).containsEntry(1L, "租户甲").doesNotContainKey(999L);
        assertThat(resolver.resolve("sys.tenant", 999L)).isNull();
    }

    @Test
    void emptyOrNullInput_shortCircuits_noLookupCall() {
        StubLookup stub = new StubLookup("sys.tenant", Map.of(1L, "租户甲"));
        EntityNameResolver resolver = new EntityNameResolver(List.of(stub));

        assertThat(resolver.resolve("sys.tenant", List.of())).isEmpty();
        assertThat(resolver.resolve("sys.tenant", (Long) null)).isNull();
        assertThat(stub.calls).isZero();
    }

    @Test
    void duplicateEntityType_throwsAtConstruction() {
        assertThatThrownBy(() -> new EntityNameResolver(List.of(
                new StubLookup("sys.tenant", Map.of()),
                new StubLookup("sys.tenant", Map.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sys.tenant");
    }

    @Test
    void blankEntityType_throwsAtConstruction() {
        assertThatThrownBy(() -> new EntityNameResolver(List.of(
                new StubLookup("", Map.of()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("entityType 不能为空");
    }
}
