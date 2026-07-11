package com.wensaas.core.impl.permission.eval;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.impl.permission.datascope.DataScope;

class UserPermissionsTest {

    @Test
    void hasPermission_simple() {
        UserPermissions up = new UserPermissions(Set.of("a", "b"), DataScope.SELF, Set.of());
        assertThat(up.hasPermission("a")).isTrue();
        assertThat(up.hasPermission("c")).isFalse();
    }

    @Test
    void hasAny_atLeastOneHits() {
        UserPermissions up = new UserPermissions(Set.of("b"), DataScope.SELF, Set.of());
        assertThat(up.hasAny(new String[]{"a", "b", "c"})).isTrue();
        assertThat(up.hasAny(new String[]{"x", "y"})).isFalse();
        assertThat(up.hasAny(new String[0])).isFalse();
    }

    @Test
    void copies_areImmutable() {
        java.util.Set<String> mutable = new java.util.HashSet<>();
        mutable.add("a");
        UserPermissions up = new UserPermissions(mutable, DataScope.ALL, Set.of(1L));
        mutable.add("b"); // 不应影响 up
        assertThat(up.permissions()).containsExactly("a");
        assertThatThrownBy(() -> up.permissions().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nulls_rejected() {
        assertThatThrownBy(() -> new UserPermissions(null, DataScope.ALL, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserPermissions(Set.of(), null, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserPermissions(Set.of(), DataScope.ALL, null))
                .isInstanceOf(NullPointerException.class);
    }
}
