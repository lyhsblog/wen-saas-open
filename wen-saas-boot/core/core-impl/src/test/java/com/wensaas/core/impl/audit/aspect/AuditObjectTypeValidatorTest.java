package com.wensaas.core.impl.audit.aspect;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditObjectTypeValidatorTest {

    private final EntityNameResolver resolver = new EntityNameResolver(List.of(new NameLookup() {
        public String entityType() { return EntityTypes.SYS_TENANT; }
        public String displayLabel() { return "租户"; }
        public Map<Long, String> batchLookup(Collection<Long> ids) { return Map.of(); }
    }));

    static class GoodBean {
        @Auditable(module = "m", action = "a", objectType = EntityTypes.SYS_TENANT)
        public void ok() {}

        @Auditable(module = "m", action = "b", objectType = "")
        public void emptyOk() {}
    }

    static class BadBean {
        @Auditable(module = "m", action = "c", objectType = "工单")
        public void bad() {}
    }

    private ListableBeanFactory factoryWith(String name, Object bean) {
        ListableBeanFactory bf = mock(ListableBeanFactory.class);
        when(bf.getBeanDefinitionNames()).thenReturn(new String[]{name});
        when(bf.getBean(name)).thenReturn(bean);
        return bf;
    }

    @Test
    void passes_whenObjectTypeIsRegisteredCodeOrEmpty() {
        new AuditObjectTypeValidator(factoryWith("good", new GoodBean()), resolver)
                .afterSingletonsInstantiated();
        // 无异常即通过
        assertThat(true).isTrue();
    }

    @Test
    void failsFast_whenObjectTypeIsUnregisteredMagicValue() {
        assertThatThrownBy(() -> new AuditObjectTypeValidator(factoryWith("bad", new BadBean()), resolver)
                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("工单")
                .hasMessageContaining("objectType");
    }
}
