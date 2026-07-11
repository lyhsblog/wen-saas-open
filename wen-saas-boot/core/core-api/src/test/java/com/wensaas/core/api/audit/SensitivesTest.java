package com.wensaas.core.api.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensitivesTest {

    @AfterEach
    void cleanup() {
        Sensitives.clearHandlers();
    }

    // ---- 单值 mask 用例 ----

    @Test
    void maskPhone_normal() {
        assertThat(Sensitives.maskPhone("13812345678")).isEqualTo("138****5678");
    }

    @Test
    void maskPhone_wrongLength_returnsStars() {
        assertThat(Sensitives.maskPhone("123")).isEqualTo("***");
        assertThat(Sensitives.maskPhone(null)).isNull();
    }

    @Test
    void maskEmail_normal() {
        assertThat(Sensitives.maskEmail("alice@x.com")).isEqualTo("a***e@x.com");
    }

    @Test
    void maskEmail_shortLocal_returnsStarsAtDomain() {
        assertThat(Sensitives.maskEmail("a@x")).isEqualTo("***@x");
        assertThat(Sensitives.maskEmail("noAt")).isEqualTo("***");
    }

    @Test
    void maskIdCard_18digits() {
        assertThat(Sensitives.maskIdCard("310101200001010011"))
                .isEqualTo("310***********0011");
    }

    @Test
    void maskIdCard_15digits() {
        assertThat(Sensitives.maskIdCard("310101900101001"))
                .isEqualTo("310********1001");
    }

    @Test
    void maskBankCard_normal() {
        assertThat(Sensitives.maskBankCard("6217001234567890"))
                .isEqualTo("6217****7890");
    }

    @Test
    void maskBankCard_tooShort() {
        assertThat(Sensitives.maskBankCard("12345")).isEqualTo("***");
    }

    // ---- maskByAnnotation 直接字段扫描 ----

    @Test
    void maskByAnnotation_appliesEachType() {
        SamplePayload payload = new SamplePayload();
        payload.name = "Alice";
        payload.phone = "13812345678";
        payload.email = "alice@x.com";
        payload.password = "secret";
        payload.note = "free text";
        payload.staticField = "static";

        Map<String, Object> masked = Sensitives.maskByAnnotation(payload);

        assertThat(masked).containsEntry("name", "Alice");
        assertThat(masked).containsEntry("phone", "138****5678");
        assertThat(masked).containsEntry("email", "a***e@x.com");
        assertThat(masked).containsEntry("password", "***");
        assertThat(masked).containsEntry("note", "free text");
        assertThat(masked).doesNotContainKey("staticField");
        assertThat(masked).doesNotContainKey("transientField");
    }

    @Test
    void maskByAnnotation_nullDto_returnsNull() {
        assertThat(Sensitives.maskByAnnotation(null)).isNull();
    }

    @Test
    void maskByAnnotation_nullField_keepsNull() {
        SamplePayload payload = new SamplePayload();
        payload.name = null;
        payload.phone = null;
        Map<String, Object> masked = Sensitives.maskByAnnotation(payload);
        assertThat(masked).containsEntry("name", null);
        assertThat(masked).containsEntry("phone", null);
    }

    @Test
    void maskByAnnotation_customHandler() {
        Sensitives.registerHandler(new SensitiveHandler() {
            @Override public String name() { return "uppercase"; }
            @Override public Object mask(Object raw) { return raw == null ? null : raw.toString().toUpperCase(); }
        });
        CustomMaskedPayload payload = new CustomMaskedPayload();
        payload.value = "hello";
        Map<String, Object> masked = Sensitives.maskByAnnotation(payload);
        assertThat(masked).containsEntry("value", "HELLO");
    }

    @Test
    void maskByAnnotation_nestedNotRecursive_v1() {
        // v1 不递归：嵌套对象内的 @Sensitive 字段不会被脱敏
        OuterDto outer = new OuterDto();
        outer.label = "L";
        outer.inner = new SamplePayload();
        outer.inner.phone = "13800000000";
        Map<String, Object> masked = Sensitives.maskByAnnotation(outer);
        assertThat(masked).containsEntry("label", "L");
        // inner 字段值原样保留（未被 maskByAnnotation 递归）
        assertThat(masked.get("inner")).isInstanceOf(SamplePayload.class);
        assertThat(((SamplePayload) masked.get("inner")).phone).isEqualTo("13800000000");
    }

    // ---- 测试 fixture ----

    static class SamplePayload {
        String name;
        @Sensitive(SensitiveType.PHONE) String phone;
        @Sensitive(SensitiveType.EMAIL) String email;
        @Sensitive(SensitiveType.REMOVE) String password;
        String note;
        static String staticField;
        transient String transientField;
    }

    static class CustomMaskedPayload {
        @Sensitive(value = SensitiveType.CUSTOM, handler = "uppercase") String value;
    }

    static class OuterDto {
        String label;
        SamplePayload inner;
    }
}
