package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.def.DataPermissionTableDef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataPermissionTableRegistryTest {

    @Test
    void registerAndLookup_caseInsensitive() {
        DataPermissionTableRegistry r = new DataPermissionTableRegistry();
        r.register("crm", new DataPermissionTableDef("crm.customer", "客户",
                "CRM_Customer", "created_by", "created_by_dept"));
        assertThat(r.findByKey("crm_customer")).isPresent();
        assertThat(r.findOwnerModule("crm_customer")).contains("crm");
        assertThat(r.size()).isEqualTo(1);
    }

    @Test
    void nullArgs_rejectedByDef() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new DataPermissionTableDef("r", "rn", null, "a", "b"))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new DataPermissionTableDef("r", "rn", "t", " ", "b"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
