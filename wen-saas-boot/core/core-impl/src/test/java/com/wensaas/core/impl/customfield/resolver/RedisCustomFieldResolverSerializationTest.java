package com.wensaas.core.impl.customfield.resolver;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldQuota;
import com.wensaas.core.api.customfield.CustomFieldType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCustomFieldResolverSerializationTest {

    @Test
    void defs_roundTrip() {
        List<CustomFieldDef> src = List.of(
                new CustomFieldDef(1L, 9001L, "crm.customer", "source", "来源",
                        CustomFieldType.ENUM, true, true, null, "GENDER", null, 1),
                new CustomFieldDef(2L, 9001L, "crm.customer", "score", "评分",
                        CustomFieldType.NUMBER, false, false, "0", null, null, 2));

        String json = RedisCustomFieldResolver.serializeDefs(src);
        List<CustomFieldDef> back = RedisCustomFieldResolver.deserializeDefs(json);

        assertThat(back).hasSize(2);
        assertThat(back.get(0).fieldCode()).isEqualTo("source");
        assertThat(back.get(0).type()).isEqualTo(CustomFieldType.ENUM);
        assertThat(back.get(0).refDictCode()).isEqualTo("GENDER");
        assertThat(back.get(0).indexed()).isTrue();
        assertThat(back.get(1).type()).isEqualTo(CustomFieldType.NUMBER);
        assertThat(back.get(1).defaultValue()).isEqualTo("0");
    }

    @Test
    void quota_roundTrip() {
        CustomFieldQuota src = new CustomFieldQuota(9001L, 25, 4, 6, 2);
        String json = RedisCustomFieldResolver.serializeQuota(src);
        CustomFieldQuota back = RedisCustomFieldResolver.deserializeQuota(json);
        assertThat(back.tenantId()).isEqualTo(9001L);
        assertThat(back.fieldQuota()).isEqualTo(25);
        assertThat(back.fieldRemaining()).isEqualTo(21);
        assertThat(back.indexUsed()).isEqualTo(2);
    }

    @Test
    void keys_format() {
        assertThat(RedisCustomFieldResolver.defsKey(9001L, "crm.customer"))
                .isEqualTo("wen-saas-boot:customfield:defs:9001:crm.customer");
        assertThat(RedisCustomFieldResolver.quotaKey(9001L))
                .isEqualTo("wen-saas-boot:customfield:quota:9001");
    }
}
