package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.tenant.TenantPlan;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TenantPlanTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void neverExpiring_isActive() {
        TenantPlan p = new TenantPlan("base", "2026-05-01T00:00:00Z", null, null);
        assertThat(p.isActive(NOW)).isTrue();
        assertThat(p.isExpiredOnly(NOW)).isFalse();
    }

    @Test
    void notYetExpired_isActive() {
        TenantPlan p = new TenantPlan("crm-pro",
                "2026-05-01T00:00:00Z", "2026-07-31T23:59:59Z", null);
        assertThat(p.isActive(NOW)).isTrue();
        assertThat(p.isExpiredOnly(NOW)).isFalse();
    }

    @Test
    void expired_isNotActive_butExpiredOnly() {
        TenantPlan p = new TenantPlan("crm-trial",
                "2026-03-01T00:00:00Z", "2026-05-31T23:59:59Z", null);
        assertThat(p.isActive(NOW)).isFalse();
        assertThat(p.isExpiredOnly(NOW)).isTrue();
    }

    @Test
    void revoked_isNeitherActiveNorExpiredOnly() {
        TenantPlan p = new TenantPlan("crm-pro",
                "2026-05-01T00:00:00Z", "2026-07-31T23:59:59Z",
                "2026-05-15T00:00:00Z");
        assertThat(p.isActive(NOW)).isFalse();
        assertThat(p.isExpiredOnly(NOW)).isFalse();
    }

    @Test
    void revokedAndExpired_revokedTakesPrecedence_neitherActiveNorExpiredOnly() {
        TenantPlan p = new TenantPlan("crm-pro",
                "2026-03-01T00:00:00Z", "2026-05-01T00:00:00Z",
                "2026-04-01T00:00:00Z");
        assertThat(p.isActive(NOW)).isFalse();
        assertThat(p.isExpiredOnly(NOW)).isFalse();
    }

    @Test
    void invalidExpiresAt_fallbackToNow_treatedAsExpired() {
        // parse 失败时保守视为"刚刚过期"，避免脏数据把整租户 capabilities 拖崩
        TenantPlan p = new TenantPlan("broken", "2026-05-01T00:00:00Z", "not-a-date", null);
        assertThat(p.isActive(NOW)).isFalse();
        assertThat(p.isExpiredOnly(NOW)).isTrue();
    }
}
