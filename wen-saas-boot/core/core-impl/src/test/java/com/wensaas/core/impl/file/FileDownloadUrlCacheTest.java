package com.wensaas.core.impl.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileDownloadUrlCacheTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private FileDownloadUrlCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        FileProperties props = new FileProperties();
        props.setDownloadUrlTtl(Duration.ofMinutes(4));
        cache = new FileDownloadUrlCache(redis, props);
    }

    @Test
    void get_hit() {
        when(ops.get("wen-saas-boot:file:url:f1")).thenReturn("https://x");
        assertThat(cache.get("f1")).contains("https://x");
    }

    @Test
    void get_miss() {
        when(ops.get(any())).thenReturn(null);
        assertThat(cache.get("f1")).isEmpty();
    }

    @Test
    void put_writesWithTtl() {
        cache.put("f1", "https://x");
        verify(ops).set("wen-saas-boot:file:url:f1", "https://x", Duration.ofMinutes(4));
    }

    @Test
    void put_zeroTtl_skip() {
        FileProperties props = new FileProperties();
        props.setDownloadUrlTtl(Duration.ZERO);
        FileDownloadUrlCache c2 = new FileDownloadUrlCache(redis, props);
        c2.put("f1", "https://x");
        verify(ops, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void evict_deletes() {
        cache.evict("f1");
        verify(redis).delete("wen-saas-boot:file:url:f1");
    }
}
