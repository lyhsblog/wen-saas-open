package com.wensaas.core.api.file;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FileMetadataTest {

    @Test
    void deletedAndActive_flags() {
        FileMetadata active = new FileMetadata("f1", 1L, "a.txt", "text/plain", 5,
                FileVisibility.FILE_CENTER, null, null, null, null,
                FileBackendType.LOCAL, null, "k1", "active", 7L,
                LocalDateTime.now(), null);
        assertThat(active.active()).isTrue();
        assertThat(active.deleted()).isFalse();

        FileMetadata deleted = new FileMetadata("f1", 1L, "a.txt", "text/plain", 5,
                FileVisibility.FILE_CENTER, null, null, null, null,
                FileBackendType.LOCAL, null, "k1", "active", 7L,
                LocalDateTime.now(), LocalDateTime.now());
        assertThat(deleted.deleted()).isTrue();

        FileMetadata uploading = new FileMetadata("f1", 1L, "a.txt", "text/plain", 5,
                FileVisibility.FILE_CENTER, null, null, null, null,
                FileBackendType.LOCAL, null, "k1", "uploading", 7L,
                LocalDateTime.now(), null);
        assertThat(uploading.active()).isFalse();
    }
}
