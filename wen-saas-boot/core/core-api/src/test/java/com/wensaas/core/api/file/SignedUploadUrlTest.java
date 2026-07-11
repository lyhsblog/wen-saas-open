package com.wensaas.core.api.file;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedUploadUrlTest {

    @Test
    void headersIsImmutableSnapshot() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("Content-Type", "text/plain");
        SignedUploadUrl url = new SignedUploadUrl("f1", "http://x", "PUT",
                mutable, Instant.now().plusSeconds(60));
        mutable.put("X-Other", "v");
        assertThat(url.headers()).containsOnlyKeys("Content-Type");
    }

    @Test
    void nullHeaders_defaultsEmpty() {
        SignedUploadUrl url = new SignedUploadUrl("f1", "http://x", "PUT", null,
                Instant.now().plusSeconds(60));
        assertThat(url.headers()).isEmpty();
    }

    @Test
    void nullFileId_throws() {
        assertThatThrownBy(() -> new SignedUploadUrl(null, "http://x", "PUT", null,
                Instant.now().plusSeconds(60)))
                .isInstanceOf(NullPointerException.class);
    }
}
