package com.wensaas.core.api.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void setGetClear() {
        assertThat(TenantContext.get()).isNull();
        TenantContext.set(42L);
        assertThat(TenantContext.get()).isEqualTo(42L);
        TenantContext.clear();
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void requireWhenUnset_throws() {
        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TenantContext 未设置");
    }

    @Test
    void requireReturnsCurrent() {
        TenantContext.set(7L);
        assertThat(TenantContext.require()).isEqualTo(7L);
    }

    @Test
    void runAs_restoresOriginalWhenWasNull() {
        assertThat(TenantContext.get()).isNull();
        TenantContext.runAs(99L, () -> assertThat(TenantContext.get()).isEqualTo(99L));
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void runAs_restoresOriginalWhenWasSet() {
        TenantContext.set(1L);
        TenantContext.runAs(99L, () -> assertThat(TenantContext.get()).isEqualTo(99L));
        assertThat(TenantContext.get()).isEqualTo(1L);
    }

    @Test
    void runAs_nestedRestoresLayerByLayer() {
        TenantContext.set(1L);
        TenantContext.runAs(2L, () -> {
            assertThat(TenantContext.get()).isEqualTo(2L);
            TenantContext.runAs(3L, () -> assertThat(TenantContext.get()).isEqualTo(3L));
            assertThat(TenantContext.get()).isEqualTo(2L);
        });
        assertThat(TenantContext.get()).isEqualTo(1L);
    }

    @Test
    void runAs_restoresOnException() {
        TenantContext.set(1L);
        try {
            TenantContext.runAs(2L, () -> { throw new RuntimeException("boom"); });
        } catch (RuntimeException ignored) {
        }
        assertThat(TenantContext.get()).isEqualTo(1L);
    }

    @Test
    void callAs_returnsValue() {
        assertThat(TenantContext.callAs(5L, () -> TenantContext.require() * 2)).isEqualTo(10L);
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void contextNotInheritedByChildThread() throws InterruptedException {
        TenantContext.set(123L);
        AtomicReference<Long> childSaw = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread(() -> { childSaw.set(TenantContext.get()); latch.countDown(); });
        t.start();
        latch.await();
        assertThat(childSaw.get()).isNull();
    }
}
