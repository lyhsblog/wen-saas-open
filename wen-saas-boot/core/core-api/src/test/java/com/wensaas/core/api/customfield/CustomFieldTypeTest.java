package com.wensaas.core.api.customfield;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomFieldTypeTest {

    @Test
    void fromCode_allKnownValues() {
        assertThat(CustomFieldType.fromCode("text")).isEqualTo(CustomFieldType.TEXT);
        assertThat(CustomFieldType.fromCode("long_text")).isEqualTo(CustomFieldType.LONG_TEXT);
        assertThat(CustomFieldType.fromCode("number")).isEqualTo(CustomFieldType.NUMBER);
        assertThat(CustomFieldType.fromCode("date")).isEqualTo(CustomFieldType.DATE);
        assertThat(CustomFieldType.fromCode("bool")).isEqualTo(CustomFieldType.BOOL);
        assertThat(CustomFieldType.fromCode("enum")).isEqualTo(CustomFieldType.ENUM);
        assertThat(CustomFieldType.fromCode("ref")).isEqualTo(CustomFieldType.REF);
    }

    @Test
    void fromCode_unknown_throws() {
        assertThatThrownBy(() -> CustomFieldType.fromCode("foobar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知 CustomFieldType");
    }

    @Test
    void code_isStable() {
        // 持久化字符串契约，改动需配套 V*.sql 迁移
        assertThat(CustomFieldType.TEXT.code()).isEqualTo("text");
        assertThat(CustomFieldType.LONG_TEXT.code()).isEqualTo("long_text");
    }
}
