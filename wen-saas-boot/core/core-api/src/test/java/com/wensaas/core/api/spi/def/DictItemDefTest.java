package com.wensaas.core.api.spi.def;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictItemDefTest {

    @Test
    void ofFactory() {
        DictItemDef item = DictItemDef.of("MALE", "男");
        assertThat(item.code()).isEqualTo("MALE");
        assertThat(item.name()).isEqualTo("男");
        assertThat(item.parentCode()).isNull();
        assertThat(item.sortOrder()).isNull();
        assertThat(item.extra()).isEmpty();
    }

    @Test
    void blankCode_throws() {
        assertThatThrownBy(() -> new DictItemDef("", "N", null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @Test
    void blankName_throws() {
        assertThatThrownBy(() -> new DictItemDef("C", " ", null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }
}
