package com.wensaas.core.impl.async;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSnapshotTest {

    @AfterEach
    void cleanup() {
        UserContext.clear();
        TenantContext.clear();
        RequestContext.clear();
        MDC.clear();
    }

    @Test
    void capture_thenRestore_setsAll() {
        UserPrincipal p = new UserPrincipal(7L, 9L, "alice", "sess-1", false, 11L, false, null);
        UserContext.set(p);
        TenantContext.set(9L);
        RequestContext.set("req-A");
        MDC.put("k", "v");
        ContextSnapshot snap = ContextSnapshot.capture();

        UserContext.clear();
        TenantContext.clear();
        RequestContext.clear();
        MDC.clear();

        try (var ignored = snap.restore()) {
            assertThat(UserContext.get()).isEqualTo(p);
            assertThat(TenantContext.get()).isEqualTo(9L);
            assertThat(RequestContext.get()).isEqualTo("req-A");
            assertThat(MDC.get("k")).isEqualTo("v");
        }
        // close 后回到 restore 前空状态
        assertThat(UserContext.get()).isNull();
        assertThat(TenantContext.get()).isNull();
        assertThat(RequestContext.get()).isNull();
        assertThat(MDC.get("k")).isNull();
    }

    @Test
    void restore_isReentrant_preservesPrevious() {
        UserPrincipal outer = new UserPrincipal(1L, 1L, "outer", "s-out", false, null, false, null);
        UserContext.set(outer);
        TenantContext.set(1L);

        ContextSnapshot inner = new ContextSnapshot(
                new UserPrincipal(2L, 2L, "inner", "s-in", false, null, false, null), 2L, "req-inner", null);
        try (var ignored = inner.restore()) {
            assertThat(UserContext.get().username()).isEqualTo("inner");
            assertThat(TenantContext.get()).isEqualTo(2L);
        }
        // close 后还原到外层 outer
        assertThat(UserContext.get()).isEqualTo(outer);
        assertThat(TenantContext.get()).isEqualTo(1L);
    }

    @Test
    void capture_emptyContext_yieldsAllNull() {
        ContextSnapshot snap = ContextSnapshot.capture();
        assertThat(snap.user()).isNull();
        assertThat(snap.tenantId()).isNull();
        assertThat(snap.requestId()).isNull();
        // MDC may be null or empty map depending on impl
        assertThat(snap.mdc() == null || snap.mdc().isEmpty()).isTrue();
    }

    @Test
    void restore_nullSnapshot_clearsContext() {
        // 提前设值
        UserContext.set(new UserPrincipal(1L, 1L, "x", "s", false, null, false, null));
        TenantContext.set(1L);

        ContextSnapshot snap = new ContextSnapshot(null, null, null, null);
        try (var ignored = snap.restore()) {
            assertThat(UserContext.get()).isNull();
            assertThat(TenantContext.get()).isNull();
            assertThat(RequestContext.get()).isNull();
        }
    }

    @Test
    void restore_mdc_appliesAndRestores() {
        MDC.put("outer", "v-outer");
        ContextSnapshot snap = new ContextSnapshot(null, null, null,
                java.util.Map.of("inner", "v-inner"));
        try (var ignored = snap.restore()) {
            assertThat(MDC.get("inner")).isEqualTo("v-inner");
            assertThat(MDC.get("outer")).isNull();
        }
        assertThat(MDC.get("outer")).isEqualTo("v-outer");
    }

    @Test
    void capture_isImmutableSnapshot() {
        UserContext.set(new UserPrincipal(1L, 1L, "a", "s", false, null, false, null));
        TenantContext.set(1L);
        ContextSnapshot snap = ContextSnapshot.capture();

        TenantContext.set(99L);
        UserContext.set(new UserPrincipal(99L, 99L, "z", "s99", false, null, false, null));

        // snap 不应被后续 set 影响
        assertThat(snap.tenantId()).isEqualTo(1L);
        assertThat(snap.user().username()).isEqualTo("a");
    }
}
