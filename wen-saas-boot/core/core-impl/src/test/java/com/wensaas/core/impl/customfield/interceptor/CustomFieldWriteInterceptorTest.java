package com.wensaas.core.impl.customfield.interceptor;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.customfield.CustomFieldExtension;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.tenant.TenantContext;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.customfield.def.CustomFieldValueValidator;
import com.wensaas.core.impl.customfield.resolver.CustomFieldResolver;

class CustomFieldWriteInterceptorTest {

    private CustomFieldResolver resolver;
    private CustomFieldValueValidator validator;
    private CustomFieldWriteInterceptor interceptor;
    private MappedStatement msInsert;
    private MappedStatement msSelect;

    @CustomFieldExtension(objectCode = "crm.customer")
    static class Customer {
        Long id;
        Map<String, Object> extra;
    }

    static class PlainEntity {
        Long id;
    }

    @BeforeEach
    void setUp() {
        CustomFieldEntitySupport.clearCache();
        resolver = mock(CustomFieldResolver.class);
        validator = mock(CustomFieldValueValidator.class);
        interceptor = new CustomFieldWriteInterceptor(resolver, validator);
        msInsert = mock(MappedStatement.class);
        when(msInsert.getId()).thenReturn("any.id.insert");
        when(msInsert.getSqlCommandType()).thenReturn(SqlCommandType.INSERT);
        msSelect = mock(MappedStatement.class);
        when(msSelect.getId()).thenReturn("any.id.select");
        when(msSelect.getSqlCommandType()).thenReturn(SqlCommandType.SELECT);
        TenantContext.set(9601L);
        when(resolver.resolveDefs(anyLong(), anyString()))
                .thenReturn(List.of(def("cf_source", CustomFieldType.TEXT)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void selectCommand_skipsValidate() {
        Customer c = new Customer();
        c.extra = new HashMap<>(Map.of("cf_source", "WEB"));
        interceptor.beforeUpdate(null, msSelect, c);
        verify(validator, never()).validate(any(), any());
    }

    @Test
    void singleEntity_insert_validates() {
        Customer c = new Customer();
        c.extra = new HashMap<>(Map.of("cf_source", "WEB"));
        interceptor.beforeUpdate(null, msInsert, c);
        verify(validator).validate(any(), eq(c.extra));
    }

    @Test
    void emptyExtra_skipsValidate() {
        Customer c = new Customer();
        c.extra = Map.of();
        interceptor.beforeUpdate(null, msInsert, c);
        verify(validator, never()).validate(any(), any());
    }

    @Test
    void nullExtra_skipsValidate() {
        Customer c = new Customer();
        interceptor.beforeUpdate(null, msInsert, c);
        verify(validator, never()).validate(any(), any());
    }

    @Test
    void collectionOfEntities_eachValidated() {
        Customer a = new Customer();
        a.extra = new HashMap<>(Map.of("cf_source", "WEB"));
        Customer b = new Customer();
        b.extra = new HashMap<>(Map.of("cf_source", "APP"));
        interceptor.beforeUpdate(null, msInsert, List.of(a, b));
        verify(validator).validate(any(), eq(a.extra));
        verify(validator).validate(any(), eq(b.extra));
    }

    @Test
    void paramMapEt_validated() {
        Customer c = new Customer();
        c.extra = new HashMap<>(Map.of("cf_source", "WEB"));
        Map<String, Object> param = new HashMap<>();
        param.put("et", c);
        param.put("ew", new Object()); // wrapper 等其他 key 不处理
        interceptor.beforeUpdate(null, msInsert, param);
        verify(validator).validate(any(), eq(c.extra));
    }

    @Test
    void paramMapList_validatesEachInList() {
        Customer c = new Customer();
        c.extra = new HashMap<>(Map.of("cf_source", "WEB"));
        Map<String, Object> param = new HashMap<>();
        param.put("list", List.of(c));
        interceptor.beforeUpdate(null, msInsert, param);
        verify(validator).validate(any(), eq(c.extra));
    }

    @Test
    void plainEntityWithoutAnnotation_skips() {
        PlainEntity p = new PlainEntity();
        p.id = 1L;
        interceptor.beforeUpdate(null, msInsert, p);
        verify(validator, never()).validate(any(), any());
    }

    @Test
    void validatorThrows_propagates() {
        Customer c = new Customer();
        c.extra = new HashMap<>(Map.of("cf_source", "WEB"));
        doThrow(new CustomFieldException(CustomFieldException.Code.INVALID_VALUE, "x"))
                .when(validator).validate(any(), any());
        assertThatThrownBy(() -> interceptor.beforeUpdate(null, msInsert, c))
                .isInstanceOf(CustomFieldException.class);
    }

    @Test
    void emptyDefs_skips() {
        when(resolver.resolveDefs(anyLong(), anyString())).thenReturn(List.of());
        Customer c = new Customer();
        c.extra = new HashMap<>(Map.of("cf_source", "WEB"));
        assertThatCode(() -> interceptor.beforeUpdate(null, msInsert, c))
                .doesNotThrowAnyException();
        verify(validator, never()).validate(any(), any());
    }

    private static CustomFieldDef def(String code, CustomFieldType type) {
        return new CustomFieldDef(1L, 9601L, "crm.customer", code, "F-" + code,
                type, false, false, null, null, null, 0);
    }
}
