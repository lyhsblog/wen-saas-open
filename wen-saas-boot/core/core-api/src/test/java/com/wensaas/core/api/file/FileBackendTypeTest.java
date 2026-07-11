package com.wensaas.core.api.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileBackendTypeTest {

    @Test
    void codes_areStable() {
        assertThat(FileBackendType.LOCAL.code()).isEqualTo("local");
        assertThat(FileBackendType.ALIYUN_OSS.code()).isEqualTo("aliyun_oss");
        assertThat(FileBackendType.TENCENT_COS.code()).isEqualTo("tencent_cos");
    }

    @Test
    void fromCode_roundTrip() {
        assertThat(FileBackendType.fromCode("local")).isEqualTo(FileBackendType.LOCAL);
        assertThat(FileBackendType.fromCode("aliyun_oss")).isEqualTo(FileBackendType.ALIYUN_OSS);
        assertThat(FileBackendType.fromCode("tencent_cos")).isEqualTo(FileBackendType.TENCENT_COS);
    }

    @Test
    void fromCode_unknown_throws() {
        assertThatThrownBy(() -> FileBackendType.fromCode("aws_s3"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
