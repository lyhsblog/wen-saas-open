package com.wensaas.core.api.customfield;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomFieldExtensionAnnotationTest {

    @CustomFieldExtension(objectCode = "crm.customer")
    static class WithDefault {}

    @CustomFieldExtension(objectCode = "ops.lead", extraField = "ext_payload")
    static class WithCustomField {}

    @Test
    void defaultExtraField_isExtra() {
        CustomFieldExtension ann = WithDefault.class.getAnnotation(CustomFieldExtension.class);
        assertThat(ann.objectCode()).isEqualTo("crm.customer");
        assertThat(ann.extraField()).isEqualTo("extra");
    }

    @Test
    void customExtraField_isPickedUp() {
        CustomFieldExtension ann = WithCustomField.class.getAnnotation(CustomFieldExtension.class);
        assertThat(ann.objectCode()).isEqualTo("ops.lead");
        assertThat(ann.extraField()).isEqualTo("ext_payload");
    }
}
