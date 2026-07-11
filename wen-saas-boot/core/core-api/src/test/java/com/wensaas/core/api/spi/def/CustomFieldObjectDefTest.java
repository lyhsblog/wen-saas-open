package com.wensaas.core.api.spi.def;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomFieldObjectDefTest {

    @Test
    void of_factory_setsDescriptionToNull() {
        CustomFieldObjectDef def = CustomFieldObjectDef.of("crm.customer", "customer", "extra");
        assertThat(def.code()).isEqualTo("crm.customer");
        assertThat(def.tableName()).isEqualTo("customer");
        assertThat(def.extraColumnName()).isEqualTo("extra");
        assertThat(def.description()).isNull();
    }

    @Test
    void blank_code_rejected() {
        assertThatThrownBy(() -> new CustomFieldObjectDef("", "t", "e", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blank_tableName_rejected() {
        assertThatThrownBy(() -> new CustomFieldObjectDef("c", " ", "e", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blank_extraColumnName_rejected() {
        assertThatThrownBy(() -> new CustomFieldObjectDef("c", "t", "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
