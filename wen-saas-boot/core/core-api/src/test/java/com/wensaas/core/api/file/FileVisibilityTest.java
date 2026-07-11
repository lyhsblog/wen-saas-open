package com.wensaas.core.api.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileVisibilityTest {

    @Test
    void codes_areStable() {
        assertThat(FileVisibility.FILE_CENTER.code()).isEqualTo("file-center");
        assertThat(FileVisibility.BUSINESS_ONLY.code()).isEqualTo("business-only");
    }

    @Test
    void fromCode_roundTrip() {
        assertThat(FileVisibility.fromCode("file-center")).isEqualTo(FileVisibility.FILE_CENTER);
        assertThat(FileVisibility.fromCode("business-only")).isEqualTo(FileVisibility.BUSINESS_ONLY);
    }

    @Test
    void fromCode_unknown_throws() {
        assertThatThrownBy(() -> FileVisibility.fromCode("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FileVisibility");
    }
}
