package com.wensaas.core.impl.auth;

import com.wensaas.core.impl.auth.password.PasswordStrengthValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordStrengthValidatorTest {

    @Test
    void tooShort_fails() {
        assertThat(PasswordStrengthValidator.evaluate("Ab1!").passed()).isFalse();
    }

    @Test
    void onlyTwoClasses_fails() {
        // 长度够但只有小写+数字（2 类）
        assertThat(PasswordStrengthValidator.evaluate("abcdefgh12").passed()).isFalse();
    }

    @Test
    void threeClassesAndLength_passes() {
        // 长度够 + 小写 + 大写 + 数字（3 类）
        PasswordStrengthValidator.Result r = PasswordStrengthValidator.evaluate("Abcdefg123");
        assertThat(r.passed()).isTrue();
        assertThat(r.strength()).isEqualTo(PasswordStrengthValidator.Strength.STRONG);
    }

    @Test
    void allFourClasses_veryStrong() {
        PasswordStrengthValidator.Result r = PasswordStrengthValidator.evaluate("Admin@2026!");
        assertThat(r.passed()).isTrue();
        assertThat(r.strength()).isEqualTo(PasswordStrengthValidator.Strength.VERY_STRONG);
    }

    @Test
    void nullOrEmpty_failsWeak() {
        assertThat(PasswordStrengthValidator.evaluate(null).passed()).isFalse();
        assertThat(PasswordStrengthValidator.evaluate("").strength())
                .isEqualTo(PasswordStrengthValidator.Strength.WEAK);
    }
}
