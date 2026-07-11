package com.wensaas.core.impl.dict;

import com.wensaas.core.api.spi.def.DictExtraDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DictExtraDataTypeTest {

    @Test
    void allTypesPresent() {
        assertThat(DictExtraDataType.values()).hasSize(6)
                .containsExactly(
                        DictExtraDataType.TEXT,
                        DictExtraDataType.NUMBER,
                        DictExtraDataType.BOOL,
                        DictExtraDataType.URL,
                        DictExtraDataType.COLOR,
                        DictExtraDataType.ICON);
    }

    @Test
    void valueOfRoundTrip() {
        for (DictExtraDataType t : DictExtraDataType.values()) {
            assertThat(DictExtraDataType.valueOf(t.name())).isSameAs(t);
        }
    }

    @Test
    void textIsFirst() {
        assertThat(DictExtraDataType.TEXT.ordinal()).isZero();
    }
}
