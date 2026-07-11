package com.wensaas.core.impl.verification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仅测 {@link VerificationCodeServiceImpl#mask} 静态脱敏。Redis/限频/TTL 等行为
 * 由集成测试覆盖（仿 PasswordResetTokenServiceImplTest 注释的惯例）。
 */
class VerificationCodeServiceImplTest {

    @Test
    void mask_cnMobile() {
        assertThat(VerificationCodeServiceImpl.mask("13812340000")).isEqualTo("138****0000");
    }

    @Test
    void mask_email() {
        assertThat(VerificationCodeServiceImpl.mask("alice@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void mask_shortTarget() {
        assertThat(VerificationCodeServiceImpl.mask("12345")).isEqualTo("1****");
    }

    @Test
    void mask_blank() {
        assertThat(VerificationCodeServiceImpl.mask("")).isEqualTo("");
        assertThat(VerificationCodeServiceImpl.mask(null)).isEqualTo("");
    }
}
