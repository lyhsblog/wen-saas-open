package com.wensaas.core.impl.dict;

import com.wensaas.core.api.spi.def.DictExtraDataType;
import com.wensaas.core.api.spi.def.DictExtraFieldDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictExtraSchemaValidatorTest {

    private final DictExtraSchemaValidator validator = new DictExtraSchemaValidator();

    @Test
    void emptySchema_anyValuesPass() {
        validator.validate(List.of(), Map.of("anything", "ok"));
        validator.validate(null, Map.of());
    }

    @Test
    void requiredMissing_throws() {
        List<DictExtraFieldDef> schema = List.of(
                new DictExtraFieldDef("code", "行政编码", DictExtraDataType.TEXT, true, null));
        assertThatThrownBy(() -> validator.validate(schema, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code")
                .hasMessageContaining("必填");
    }

    @Test
    void optionalMissing_pass() {
        List<DictExtraFieldDef> schema = List.of(
                new DictExtraFieldDef("level", "层级", DictExtraDataType.NUMBER, false, null));
        validator.validate(schema, Map.of());
    }

    @Test
    void numberAcceptsIntAndDouble() {
        List<DictExtraFieldDef> schema = List.of(
                new DictExtraFieldDef("level", "层级", DictExtraDataType.NUMBER, true, null));
        validator.validate(schema, Map.of("level", 1));
        validator.validate(schema, Map.of("level", 1.5));
        validator.validate(schema, Map.of("level", 100L));
    }

    @Test
    void numberRejectsString() {
        List<DictExtraFieldDef> schema = List.of(
                new DictExtraFieldDef("level", "层级", DictExtraDataType.NUMBER, true, null));
        assertThatThrownBy(() -> validator.validate(schema, Map.of("level", "1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NUMBER");
    }

    @Test
    void boolAcceptsBoolean_rejectsOthers() {
        List<DictExtraFieldDef> schema = List.of(
                new DictExtraFieldDef("flag", "标志", DictExtraDataType.BOOL, true, null));
        validator.validate(schema, Map.of("flag", true));
        validator.validate(schema, Map.of("flag", false));
        assertThatThrownBy(() -> validator.validate(schema, Map.of("flag", "true")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void urlPattern() {
        List<DictExtraFieldDef> schema = List.of(
                new DictExtraFieldDef("home", "主页", DictExtraDataType.URL, true, null));
        validator.validate(schema, Map.of("home", "https://example.com/path?x=1"));
        validator.validate(schema, Map.of("home", "http://localhost:8080"));
        assertThatThrownBy(() -> validator.validate(schema, Map.of("home", "not a url")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL");
    }

    @Test
    void colorPattern() {
        List<DictExtraFieldDef> schema = List.of(
                new DictExtraFieldDef("bg", "背景色", DictExtraDataType.COLOR, true, null));
        validator.validate(schema, Map.of("bg", "#FF6600"));
        validator.validate(schema, Map.of("bg", "#abc"));
        assertThatThrownBy(() -> validator.validate(schema, Map.of("bg", "FF6600")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COLOR");
    }

    @Test
    void unknownFieldIsIgnored() {
        List<DictExtraFieldDef> schema = List.of(
                new DictExtraFieldDef("code", "编码", DictExtraDataType.TEXT, true, null));
        // 多余的 "extra_key" 字段被忽略，不报错
        validator.validate(schema, Map.of("code", "BJ", "extra_key", 123));
    }
}
