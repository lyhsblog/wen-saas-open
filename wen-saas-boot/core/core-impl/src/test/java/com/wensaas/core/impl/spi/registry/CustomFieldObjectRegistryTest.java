package com.wensaas.core.impl.spi.registry;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.impl.spi.module.NamespaceValidator;

class CustomFieldObjectRegistryTest {

    @Test
    void registerAndFindByTableName_caseInsensitive() {
        CustomFieldObjectRegistry r = new CustomFieldObjectRegistry();
        r.register("crm", new CustomFieldObjectDef(
                "crm.customer", "CRM_Customer", "extra", null));
        assertThat(r.findByTableName("crm_customer")).isPresent();
        assertThat(r.findByTableName("CRM_CUSTOMER")).isPresent();
        assertThat(r.findByTableName(null)).isEmpty();
        assertThat(r.findByTableName("nothing")).isEmpty();
    }

    @Test
    void duplicateTableName_differentCode_throws() {
        CustomFieldObjectRegistry r = new CustomFieldObjectRegistry();
        r.register("crm", new CustomFieldObjectDef(
                "crm.customer", "crm_shared", "extra", null));
        assertThatThrownBy(() -> r.register("hr", new CustomFieldObjectDef(
                "hr.lead", "crm_shared", "extra", null)))
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("crm_shared");
    }

    @Test
    void sameCodeReregister_okIdempotent() {
        CustomFieldObjectRegistry r = new CustomFieldObjectRegistry();
        r.register("crm", new CustomFieldObjectDef(
                "crm.customer", "crm_customer", "extra", null));
        // 同 code 重复 register 不报错（v2 接受幂等覆盖；NamespaceValidator 在 SPI 启动期已先校验 code 维度）
        r.register("crm", new CustomFieldObjectDef(
                "crm.customer", "crm_customer", "extra", null));
        assertThat(r.findByTableName("crm_customer")).isPresent();
    }
}
