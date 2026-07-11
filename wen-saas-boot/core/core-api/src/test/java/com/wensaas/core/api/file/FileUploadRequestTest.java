package com.wensaas.core.api.file;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadRequestTest {

    @Test
    void nullFileName_throws() {
        assertThatThrownBy(() -> new FileUploadRequest(
                null, "text/plain", 5, new ByteArrayInputStream(new byte[5]),
                FileVisibility.FILE_CENTER, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fileName");
    }

    @Test
    void nullContent_throws() {
        assertThatThrownBy(() -> new FileUploadRequest(
                "a.txt", "text/plain", 5, null,
                FileVisibility.FILE_CENTER, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void negativeSize_throws() {
        assertThatThrownBy(() -> new FileUploadRequest(
                "a.txt", "text/plain", -1, new ByteArrayInputStream(new byte[0]),
                FileVisibility.FILE_CENTER, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void businessOnly_requiresOwnerModule() {
        assertThatThrownBy(() -> new FileUploadRequest(
                "a.txt", "text/plain", 5, new ByteArrayInputStream(new byte[5]),
                FileVisibility.BUSINESS_ONLY, null, "客户", 1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerModule");
    }

    @Test
    void fileCenter_allowsNullOwnerModule() {
        new FileUploadRequest(
                "a.txt", "text/plain", 5, new ByteArrayInputStream(new byte[5]),
                FileVisibility.FILE_CENTER, null, null, null, "/my");
    }
}
