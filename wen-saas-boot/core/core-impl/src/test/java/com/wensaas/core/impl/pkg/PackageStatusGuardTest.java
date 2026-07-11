package com.wensaas.core.impl.pkg;

import com.wensaas.core.api.cache.CacheRegion;
import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.api.pkg.PackageStatusException;
import com.wensaas.core.impl.tenant.TenantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageStatusGuardTest {

    private static final long TENANT_ID = 101L;
    private static final String MODULE = "crm";

    private TenantPlansResolver resolver;
    private PackageStatusGuard guard;

    @BeforeEach
    void setup() {
        resolver = mock(TenantPlansResolver.class);
        guard = new PackageStatusGuard(resolver, new InMemoryCacheRegionFactory(), new TenantProperties());
    }

    @Test
    void platformTenant_alwaysPasses() {
        assertThatCode(() -> guard.requireAccess(0L, MODULE, true)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireAccess(0L, MODULE, false)).doesNotThrowAnyException();
    }

    @Test
    void nullTenant_alwaysPasses() {
        assertThatCode(() -> guard.requireAccess(null, MODULE, true)).doesNotThrowAnyException();
    }

    @Test
    void activeModule_passesReadAndWrite() {
        when(resolver.resolve(TENANT_ID)).thenReturn(new TenantPlansResolver.ResolvedPlans(
                Set.of(MODULE), Set.of(), List.of()));

        assertThatCode(() -> guard.requireAccess(TENANT_ID, MODULE, true)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireAccess(TENANT_ID, MODULE, false)).doesNotThrowAnyException();
    }

    @Test
    void readonlyModule_readPasses_writeFails() {
        when(resolver.resolve(TENANT_ID)).thenReturn(new TenantPlansResolver.ResolvedPlans(
                Set.of(), Set.of(MODULE), List.of()));

        assertThatCode(() -> guard.requireAccess(TENANT_ID, MODULE, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireAccess(TENANT_ID, MODULE, true))
                .isInstanceOf(PackageStatusException.class)
                .satisfies(e -> {
                    PackageStatusException ex = (PackageStatusException) e;
                    assertThatCode(() -> {
                        assert ex.getCode() == PackageStatusException.Code.PACKAGE_READONLY;
                        assert MODULE.equals(ex.getModuleCode());
                    }).doesNotThrowAnyException();
                });
    }

    @Test
    void unauthorizedModule_failsReadAndWrite() {
        when(resolver.resolve(TENANT_ID)).thenReturn(TenantPlansResolver.ResolvedPlans.empty());

        assertThatThrownBy(() -> guard.requireAccess(TENANT_ID, MODULE, false))
                .isInstanceOf(PackageStatusException.class)
                .extracting(e -> ((PackageStatusException) e).getCode())
                .isEqualTo(PackageStatusException.Code.PACKAGE_UNAUTHORIZED);

        assertThatThrownBy(() -> guard.requireAccess(TENANT_ID, MODULE, true))
                .isInstanceOf(PackageStatusException.class)
                .extracting(e -> ((PackageStatusException) e).getCode())
                .isEqualTo(PackageStatusException.Code.PACKAGE_UNAUTHORIZED);
    }

    @Test
    void cacheHit_resolverCalledOnlyOnce() {
        when(resolver.resolve(TENANT_ID)).thenReturn(new TenantPlansResolver.ResolvedPlans(
                Set.of(MODULE), Set.of(), List.of()));

        guard.requireAccess(TENANT_ID, MODULE, false);
        guard.requireAccess(TENANT_ID, MODULE, true);

        verify(resolver, times(1)).resolve(TENANT_ID);
    }

    @Test
    void invalidate_clearsCacheEntry() {
        when(resolver.resolve(TENANT_ID)).thenReturn(new TenantPlansResolver.ResolvedPlans(
                Set.of(MODULE), Set.of(), List.of()));

        guard.requireAccess(TENANT_ID, MODULE, false);
        guard.evict(TENANT_ID);
        guard.requireAccess(TENANT_ID, MODULE, false);

        verify(resolver, times(2)).resolve(TENANT_ID);
    }

    /** 进程内假缓存：用真实 get/load/evict 语义验证 guard 的缓存行为，不依赖 Redis。 */
    private static final class InMemoryCacheRegionFactory implements CacheRegionFactory {
        @Override
        public <V> CacheRegion<V> create(String name, Duration ttl,
                                         Function<V, String> serializer, Function<String, V> deserializer) {
            return new CacheRegion<>() {
                private final Map<String, V> map = new HashMap<>();

                @Override public Optional<V> get(String key) {
                    return Optional.ofNullable(map.get(key));
                }

                @Override public V getOrLoad(String key, Supplier<V> loader) {
                    return map.computeIfAbsent(key, k -> loader.get());
                }

                @Override public void put(String key, V value) {
                    map.put(key, value);
                }

                @Override public void evict(String key) {
                    map.remove(key);
                }

                @Override public void evictAll() {
                    map.clear();
                }
            };
        }
    }
}
