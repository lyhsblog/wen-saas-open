package com.wensaas.core.impl.auth.password;

import com.wensaas.core.impl.auth.password.PasswordResetTokenServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仅测 {@link PasswordResetTokenServiceImpl#maskUsername} 静态方法。
 * {@code issue(...)} 的字段语义（token 长度 / ttl / purpose / 旧 token 作废）由
 * {@code JourneyOneIntegrationTest} 真 PG 全链路覆盖；纯 mock 单测会因 MP
 * {@code LambdaUtils} 找不到 entity TableInfo cache 而 fail，价值不大。
 */
class PasswordResetTokenServiceImplTest {

    @Test
    void maskUsername_lengthGt5_showsFirstAndLastTwo() {
        assertThat(PasswordResetTokenServiceImpl.maskUsername("abcdef")).isEqualTo("a***ef");
    }

    @Test
    void maskUsername_length4_showsFirstAndLastOne() {
        assertThat(PasswordResetTokenServiceImpl.maskUsername("abcd")).isEqualTo("a***d");
    }

    @Test
    void maskUsername_length3_showsFirstOnly() {
        assertThat(PasswordResetTokenServiceImpl.maskUsername("abc")).isEqualTo("a***");
    }

    @Test
    void maskUsername_length1_returnsAsIs() {
        assertThat(PasswordResetTokenServiceImpl.maskUsername("a")).isEqualTo("a");
    }

    @Test
    void maskUsername_null_returnsNull() {
        assertThat(PasswordResetTokenServiceImpl.maskUsername(null)).isNull();
    }
}
