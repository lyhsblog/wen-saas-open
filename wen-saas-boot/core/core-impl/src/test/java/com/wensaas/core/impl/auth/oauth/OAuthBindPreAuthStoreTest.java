package com.wensaas.core.impl.auth.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OAuth 绑定流暂存：序列化往返 + create/find/consume（mock Redis）。 */
class OAuthBindPreAuthStoreTest {

    @Test
    void serialize_deserialize_roundTrip() {
        OAuthBindPreAuth original = new OAuthBindPreAuth("dingtalk", "ext-1", "uni-1", "昵称", "http://a",
                Instant.parse("2026-06-03T10:00:00Z"));
        String json = OAuthBindPreAuthStore.serialize(original);
        assertThat(OAuthBindPreAuthStore.deserialize(json)).isEqualTo(original);
    }

    @SuppressWarnings("unchecked")
    @Test
    void create_storesJsonWithTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        OAuthBindPreAuthStore store = new OAuthBindPreAuthStore(redis);

        String token = store.create(new OAuthBindPreAuth("dingtalk", "e", "u", "n", null, Instant.now()));

        assertThat(token).isNotBlank();
        verify(ops).set(eq(OAuthBindPreAuthStore.KEY_PREFIX + token), any(String.class),
                eq(OAuthBindPreAuthStore.TTL));
    }

    @SuppressWarnings("unchecked")
    @Test
    void find_presentThenConsumeDeletes() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        String json = OAuthBindPreAuthStore.serialize(
                new OAuthBindPreAuth("dingtalk", "e", "u", "n", null, Instant.parse("2026-06-03T10:00:00Z")));
        when(ops.get(OAuthBindPreAuthStore.KEY_PREFIX + "tk")).thenReturn(json);
        OAuthBindPreAuthStore store = new OAuthBindPreAuthStore(redis);

        Optional<OAuthBindPreAuth> found = store.find("tk");
        assertThat(found).isPresent();
        assertThat(found.get().externalId()).isEqualTo("e");

        store.consume("tk");
        verify(redis).delete(OAuthBindPreAuthStore.KEY_PREFIX + "tk");
    }

    @SuppressWarnings("unchecked")
    @Test
    void find_blankOrMissing_empty() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(any())).thenReturn(null);
        OAuthBindPreAuthStore store = new OAuthBindPreAuthStore(redis);

        assertThat(store.find("missing")).isEmpty();
        assertThat(store.find(null)).isEmpty();
        assertThat(store.find("  ")).isEmpty();
    }
}
