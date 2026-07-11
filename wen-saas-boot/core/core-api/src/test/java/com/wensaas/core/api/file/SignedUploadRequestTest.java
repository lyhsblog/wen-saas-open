package com.wensaas.core.api.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedUploadRequestTest {

    @Test
    void businessOnly_requiresOwnerModule() {
        assertThatThrownBy(() -> new SignedUploadRequest(
                "a.txt", "text/plain", 5, FileVisibility.BUSINESS_ONLY,
                null, "客户", 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerModule");
    }

    @Test
    void negativeSize_throws() {
        assertThatThrownBy(() -> new SignedUploadRequest(
                "a.txt", "text/plain", -1, FileVisibility.FILE_CENTER,
                null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valid_ok() {
        new SignedUploadRequest("a.txt", "text/plain", 5,
                FileVisibility.FILE_CENTER, null, null, null, "/my");
    }
}
