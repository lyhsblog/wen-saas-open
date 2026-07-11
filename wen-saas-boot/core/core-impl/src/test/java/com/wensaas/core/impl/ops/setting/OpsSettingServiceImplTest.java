package com.wensaas.core.impl.ops.setting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wensaas.core.api.cache.CacheRegion;
import com.wensaas.core.api.cache.CacheRegionFactory;
import com.wensaas.core.api.ops.setting.OpsSettingService;
import com.wensaas.core.api.ops.setting.SettingCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 密钥类参数「掩码回显 / 留空·掩码=不变」契约的单测。判定按 key 名模式（secret / access_key /
 * password）与分组无关——当前种子无密钥类 key，用假想 key 验证机制以备将来新增参数时自动生效。
 * DB 交互全 mock，不连库；缓存用进程内假实现。
 */
class OpsSettingServiceImplTest {

    private static final String MASK = OpsSettingService.SECRET_VALUE_MASK;

    private SysSettingMapper mapper;
    /** 进程内缓存后备存储；预置条目即可让 get() 命中而不经 mapper。 */
    private final Map<String, String> cacheStore = new HashMap<>();
    private OpsSettingServiceImpl service;

    @BeforeEach
    void setup() {
        mapper = mock(SysSettingMapper.class);
        service = new OpsSettingServiceImpl(mapper, new ObjectMapper(),
                new InMemoryCacheRegionFactory(), new OpsSettingProperties());
    }

    // ===== 掩码回显 =====

    @Test
    void listByCategory_masksSecretKeys_keepsOthersPlain() {
        when(mapper.selectList(any())).thenReturn(List.of(
                row("service_phone", "\"400-000-0000\""),
                row("smtp_password", "\"PW-PLAIN\""),
                row("callback_access_key", "\"AK-PLAIN\"")));

        var view = service.listByCategory(SettingCategory.PLATFORM_INFO);
        Map<String, Object> byKey = new LinkedHashMap<>();
        view.items().forEach(i -> byKey.put(i.key(), i.value()));

        assertThat(byKey.get("service_phone")).isEqualTo("400-000-0000");
        assertThat(byKey.get("smtp_password")).isEqualTo(MASK);
        assertThat(byKey.get("callback_access_key")).isEqualTo(MASK);
    }

    @Test
    void listByCategory_emptySecret_notMasked() {
        when(mapper.selectList(any())).thenReturn(List.of(row("smtp_password", "\"\"")));
        var view = service.listByCategory(SettingCategory.PLATFORM_INFO);
        assertThat(view.items().getFirst().value()).isEqualTo("");
    }

    // ===== 写入契约：留空 / 掩码 = 不变 =====

    @Test
    void update_secretMaskOrBlank_skipped_othersWritten() {
        when(mapper.selectOne(any())).thenReturn(row("service_phone", "\"old\""));

        Map<String, Object> values = new HashMap<>();
        values.put("smtp_password", MASK);
        values.put("callback_access_key", "  ");
        values.put("service_phone", "400-111-2222");
        service.update(SettingCategory.PLATFORM_INFO, values, 1L);

        ArgumentCaptor<SysSetting> cap = ArgumentCaptor.forClass(SysSetting.class);
        verify(mapper).updateById(cap.capture());
        assertThat(cap.getAllValues()).hasSize(1);
        assertThat(cap.getValue().getValue()).isEqualTo("\"400-111-2222\"");
    }

    @Test
    void update_secretNewValue_written() {
        when(mapper.selectOne(any())).thenReturn(row("smtp_password", "\"old\""));

        service.update(SettingCategory.PLATFORM_INFO, Map.of("smtp_password", "new-secret"), 1L);

        ArgumentCaptor<SysSetting> cap = ArgumentCaptor.forClass(SysSetting.class);
        verify(mapper).updateById(cap.capture());
        assertThat(cap.getValue().getValue()).isEqualTo("\"new-secret\"");
    }

    // ===== helpers =====

    private static SysSetting row(String key, String jsonValue) {
        SysSetting s = new SysSetting();
        s.setId(1L);
        s.setCategory(SettingCategory.PLATFORM_INFO.dbValue());
        s.setKey(key);
        s.setValue(jsonValue);
        s.setDescription(key);
        return s;
    }

    /** 进程内 CacheRegion 假实现（不接 Redis），后备存储共享 {@link #cacheStore}。 */
    private final class InMemoryCacheRegionFactory implements CacheRegionFactory {
        @Override
        public <V> CacheRegion<V> create(String name, Duration ttl,
                                         Function<V, String> serializer,
                                         Function<String, V> deserializer) {
            return new CacheRegion<>() {
                @Override
                public Optional<V> get(String key) {
                    return Optional.ofNullable(cacheStore.get(key)).map(deserializer);
                }

                @Override
                public V getOrLoad(String key, Supplier<V> loader) {
                    String hit = cacheStore.get(key);
                    if (hit != null) {
                        return deserializer.apply(hit);
                    }
                    V loaded = loader.get();
                    cacheStore.put(key, serializer.apply(loaded));
                    return loaded;
                }

                @Override
                public void put(String key, V value) {
                    cacheStore.put(key, serializer.apply(value));
                }

                @Override
                public void evict(String key) {
                    cacheStore.remove(key);
                }

                @Override
                public void evictAll() {
                    cacheStore.clear();
                }
            };
        }
    }
}
