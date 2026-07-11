package com.wensaas.core.impl.customfield.interceptor;

import com.wensaas.core.api.customfield.CustomFieldExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomFieldEntitySupportTest {

    @BeforeEach
    void clearCache() {
        CustomFieldEntitySupport.clearCache();
    }

    static class PlainPojo {
        Map<String, Object> extra;
    }

    @CustomFieldExtension(objectCode = "crm.customer")
    static class CustomerEntity {
        Map<String, Object> extra;
    }

    @CustomFieldExtension(objectCode = "ops.lead", extraField = "ext_payload")
    static class LeadEntity {
        Map<String, Object> ext_payload;
    }

    @CustomFieldExtension(objectCode = "bad.missing_field")
    static class MissingExtraField {
        Map<String, Object> notExtra;
    }

    @CustomFieldExtension(objectCode = "bad.wrong_type")
    static class WrongType {
        String extra;
    }

    @Test
    void noAnnotation_returnsEmpty() {
        assertThat(CustomFieldEntitySupport.metaOf(PlainPojo.class)).isEmpty();
    }

    @Test
    void withAnnotation_returnsMeta_andReadsExtra() {
        Optional<CustomFieldEntitySupport.EntityMeta> metaOpt =
                CustomFieldEntitySupport.metaOf(CustomerEntity.class);
        assertThat(metaOpt).isPresent();
        CustomFieldEntitySupport.EntityMeta meta = metaOpt.get();
        assertThat(meta.objectCode()).isEqualTo("crm.customer");

        CustomerEntity e = new CustomerEntity();
        e.extra = Map.of("cf_source", "WEB");
        Map<String, Object> read = CustomFieldEntitySupport.readExtra(e, meta);
        assertThat(read).containsEntry("cf_source", "WEB");
    }

    @Test
    void customExtraField_isPickedUp() {
        Optional<CustomFieldEntitySupport.EntityMeta> metaOpt =
                CustomFieldEntitySupport.metaOf(LeadEntity.class);
        assertThat(metaOpt).isPresent();
        LeadEntity e = new LeadEntity();
        e.ext_payload = new HashMap<>(Map.of("cf_score", 95));
        Map<String, Object> read = CustomFieldEntitySupport.readExtra(e, metaOpt.get());
        assertThat(read).containsEntry("cf_score", 95);
    }

    @Test
    void cacheHit_doesNotRescan() {
        // 第一次访问触发反射；第二次直接命中缓存
        Optional<CustomFieldEntitySupport.EntityMeta> first =
                CustomFieldEntitySupport.metaOf(CustomerEntity.class);
        Optional<CustomFieldEntitySupport.EntityMeta> second =
                CustomFieldEntitySupport.metaOf(CustomerEntity.class);
        assertThat(first.get()).isSameAs(second.get());
    }

    @Test
    void missingExtraField_throws() {
        assertThatThrownBy(() -> CustomFieldEntitySupport.metaOf(MissingExtraField.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少字段");
    }

    @Test
    void wrongType_throws() {
        assertThatThrownBy(() -> CustomFieldEntitySupport.metaOf(WrongType.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("类型不是 Map");
    }
}
