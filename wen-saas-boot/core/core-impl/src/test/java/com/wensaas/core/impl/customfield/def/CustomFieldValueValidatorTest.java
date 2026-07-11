package com.wensaas.core.impl.customfield.def;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.dict.DictService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomFieldValueValidatorTest {

    private DictService dictService;
    private CustomFieldValueValidator validator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        dictService = mock(DictService.class);
        ObjectProvider<DictService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dictService);
        validator = new CustomFieldValueValidator(provider);
    }

    @Test
    void requiredField_missing_throws() {
        CustomFieldDef def = def("name", CustomFieldType.TEXT, true);
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of()))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.INVALID_VALUE);
    }

    @Test
    void textType_acceptsString_rejectsNumber() {
        CustomFieldDef def = def("name", CustomFieldType.TEXT, false);
        assertThatCode(() -> validator.validate(List.of(def), Map.of("name", "abc")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("name", 42)))
                .isInstanceOf(CustomFieldException.class);
    }

    @Test
    void textLength_over255_throws() {
        CustomFieldDef def = def("name", CustomFieldType.TEXT, false);
        String tooLong = "x".repeat(256);
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("name", tooLong)))
                .isInstanceOf(CustomFieldException.class)
                .hasMessageContaining("超过上限 255");
    }

    @Test
    void longText_allows4000_rejects4001() {
        CustomFieldDef def = def("desc", CustomFieldType.LONG_TEXT, false);
        assertThatCode(() -> validator.validate(List.of(def), Map.of("desc", "x".repeat(4000))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("desc", "x".repeat(4001))))
                .isInstanceOf(CustomFieldException.class);
    }

    @Test
    void numberType_acceptsIntDoubleBigDecimal() {
        CustomFieldDef def = def("score", CustomFieldType.NUMBER, false);
        assertThatCode(() -> validator.validate(List.of(def), Map.of("score", 1))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(List.of(def), Map.of("score", 1.5))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(List.of(def),
                Map.of("score", new java.math.BigDecimal("3.14")))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("score", "1.5")))
                .isInstanceOf(CustomFieldException.class);
    }

    @Test
    void boolType_strict() {
        CustomFieldDef def = def("active", CustomFieldType.BOOL, false);
        assertThatCode(() -> validator.validate(List.of(def), Map.of("active", true))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("active", "true")))
                .isInstanceOf(CustomFieldException.class);
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("active", 1)))
                .isInstanceOf(CustomFieldException.class);
    }

    @Test
    void dateType_acceptsMultipleForms() {
        CustomFieldDef def = def("birthday", CustomFieldType.DATE, false);
        assertThatCode(() -> validator.validate(List.of(def), Map.of("birthday", LocalDate.of(2026, 1, 1))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(List.of(def), Map.of("birthday", "2026-01-01")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(List.of(def), Map.of("birthday", "2026-01-01T12:30:00")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(List.of(def), Map.of("birthday", 1_700_000_000_000L)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("birthday", "yesterday")))
                .isInstanceOf(CustomFieldException.class);
    }

    @Test
    void enumType_valueInDict_passes() {
        CustomFieldDef def = enumDef("gender", "GENDER");
        when(dictService.batchGetItemNames(eq("GENDER"), any()))
                .thenReturn(Map.of("MALE", "男"));
        assertThatCode(() -> validator.validate(List.of(def), Map.of("gender", "MALE")))
                .doesNotThrowAnyException();
    }

    @Test
    void enumType_valueNotInDict_throws() {
        CustomFieldDef def = enumDef("gender", "GENDER");
        when(dictService.batchGetItemNames(eq("GENDER"), any())).thenReturn(Map.of());
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("gender", "ALIEN")))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.ENUM_VALUE_NOT_IN_DICT);
    }

    @Test
    void enumType_blankValue_optional_skipsDictCheck() {
        CustomFieldDef def = enumDef("gender", "GENDER"); // optional
        // 空白（空串 / 纯空格）视为未提供：不查字典、不报错（dictService 未打桩，若误查会 NPE）
        assertThatCode(() -> validator.validate(List.of(def), Map.of("gender", "")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(List.of(def), Map.of("gender", "   ")))
                .doesNotThrowAnyException();
    }

    @Test
    void enumType_blankValue_required_throwsRequired() {
        CustomFieldDef def = enumDef("gender", "GENDER", true);
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("gender", "  ")))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.INVALID_VALUE);
    }

    @Test
    void enumType_valueNotString_throws() {
        CustomFieldDef def = enumDef("gender", "GENDER");
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("gender", 1)))
                .isInstanceOf(CustomFieldException.class);
    }

    @Test
    void refType_acceptsLong_rejectsDouble() {
        CustomFieldDef def = refDef("parent", "crm.customer");
        assertThatCode(() -> validator.validate(List.of(def), Map.of("parent", 100L)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(List.of(def), Map.of("parent", 100)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("parent", 1.5)))
                .isInstanceOf(CustomFieldException.class);
        assertThatThrownBy(() -> validator.validate(List.of(def), Map.of("parent", "abc")))
                .isInstanceOf(CustomFieldException.class);
    }

    @Test
    void unknownField_inValues_isIgnored() {
        CustomFieldDef def = def("a", CustomFieldType.TEXT, false);
        assertThatCode(() -> validator.validate(List.of(def), Map.of("b", 1, "a", "ok")))
                .doesNotThrowAnyException();
    }

    @Test
    void indexByCode_buildsMap() {
        CustomFieldDef a = def("a", CustomFieldType.TEXT, false);
        CustomFieldDef b = def("b", CustomFieldType.NUMBER, false);
        Map<String, CustomFieldDef> map = CustomFieldValueValidator.indexByCode(List.of(a, b));
        assertThat(map).hasSize(2).containsKeys("a", "b");
    }

    @Test
    void ensureDictGroupExists_missingDict_throws() {
        when(dictService.getGroup("MISSING")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> validator.ensureDictGroupExists("MISSING"))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.DICT_GROUP_NOT_FOUND);
    }

    private static CustomFieldDef def(String code, CustomFieldType type, boolean required) {
        return new CustomFieldDef(1L, 9001L, "crm.customer", code, "Name",
                type, required, false, null, null, null, 0);
    }

    private static CustomFieldDef enumDef(String code, String dict) {
        return enumDef(code, dict, false);
    }

    private static CustomFieldDef enumDef(String code, String dict, boolean required) {
        return new CustomFieldDef(1L, 9001L, "crm.customer", code, "Enum",
                CustomFieldType.ENUM, required, false, null, dict, null, 0);
    }

    private static CustomFieldDef refDef(String code, String ref) {
        return new CustomFieldDef(1L, 9001L, "crm.customer", code, "Ref",
                CustomFieldType.REF, false, false, null, null, ref, 0);
    }
}
