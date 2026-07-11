package com.wensaas.core.impl.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantCodePolicyTest {

    @Test
    void validFormats() {
        for (String code : new String[]{
                "acme2", "hello", "a1-2b", "wen-saas",
                "abcdefghijklmnop"   // 16 位（上限）
        }) {
            assertThat(TenantCodePolicy.isValidFormat(code)).as(code).isTrue();
        }
    }

    @Test
    void invalidFormats() {
        for (String code : new String[]{
                "abcd",                               // 过短（<5）
                "a",                                  // 过短
                "ABCDE",                              // 大写
                "a_b_c",                              // 下划线
                "acme-",                              // 以连字符结尾
                "-acme",                              // 以连字符开头
                "ab.cd",                              // 点号
                "",                                   // 空串
                "abcdefghijklmnopq"                   // 17 位（超上限）
        }) {
            assertThat(TenantCodePolicy.isValidFormat(code)).as(code).isFalse();
        }
    }

    @Test
    void nullIsNotValid() {
        assertThat(TenantCodePolicy.isValidFormat(null)).isFalse();
    }
}
