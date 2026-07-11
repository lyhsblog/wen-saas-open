package com.wensaas.core.impl.namesource;

import com.wensaas.core.impl.namesource.ColumnRefRegistry.Ref;
import com.wensaas.core.impl.namesource.NameFillSamples.CountingLookup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisplayRefValidatorTest {

    private static EntityNameResolver resolverWithUser() {
        return new EntityNameResolver(List.of(
                new CountingLookup(NameFillSamples.USER, Map.of(1L, "张三"))));
    }

    @Test
    void allRegistered_passes() {
        ColumnRefRegistry registry = new ColumnRefRegistry(Map.of(
                "crm_customer", Map.of("owner_user_id", new Ref(NameFillSamples.USER, "负责人"))));
        DisplayRefValidator validator = new DisplayRefValidator(registry, resolverWithUser());

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void unregisteredType_failFast() {
        ColumnRefRegistry registry = new ColumnRefRegistry(Map.of(
                "crm_customer", Map.of("owner_user_id", new Ref("sys.bogus", "负责人"))));
        DisplayRefValidator validator = new DisplayRefValidator(registry, resolverWithUser());

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sys.bogus")
                .hasMessageContaining("crm_customer");
    }
}
