package com.wensaas.core.impl.file;

import com.wensaas.core.api.file.FileException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    private static final String KEY_32_BYTES = Base64.getEncoder().encodeToString(
            "test-aes-key-for-unit-only-32by!".getBytes());
    private static final String KEY_32_BYTES_V2 = Base64.getEncoder().encodeToString(
            "test-aes-key-for-unit-only-v2!!!".getBytes());

    private CredentialCipher cipher() {
        FileProperties props = new FileProperties();
        props.setEncryptionKeys(Map.of("default", KEY_32_BYTES));
        props.setActiveKeyId("default");
        return new CredentialCipher(props);
    }

    @Test
    void roundTrip() {
        CredentialCipher c = cipher();
        String enc = c.encrypt("hello world");
        assertThat(enc).isNotEqualTo("hello world");
        assertThat(c.decrypt(enc)).isEqualTo("hello world");
    }

    @Test
    void nullInputs() {
        CredentialCipher c = cipher();
        assertThat(c.encrypt(null)).isNull();
        assertThat(c.decrypt(null)).isNull();
    }

    @Test
    void ivIsRandom_sameInputDifferentCipherText() {
        CredentialCipher c = cipher();
        String a = c.encrypt("hello");
        String b = c.encrypt("hello");
        assertThat(a).isNotEqualTo(b);
        assertThat(c.decrypt(a)).isEqualTo("hello");
        assertThat(c.decrypt(b)).isEqualTo("hello");
    }

    @Test
    void tamperedCipherText_throws() {
        CredentialCipher c = cipher();
        String enc = c.encrypt("hello");
        // v2 密文格式 `default:<base64>`；只篡改冒号后面 base64 部分
        int sep = enc.indexOf(':');
        String prefix = enc.substring(0, sep + 1);
        byte[] bytes = Base64.getDecoder().decode(enc.substring(sep + 1));
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = prefix + Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> c.decrypt(tampered))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.CREDENTIAL_DECRYPT_ERROR));
    }

    @Test
    void shortKey_failsAtConstruction() {
        FileProperties props = new FileProperties();
        props.setEncryptionKeys(Map.of("default",
                Base64.getEncoder().encodeToString("too-short".getBytes())));
        props.setActiveKeyId("default");
        assertThatThrownBy(() -> new CredentialCipher(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    void missingKey_failsAtConstruction() {
        FileProperties props = new FileProperties();
        assertThatThrownBy(() -> new CredentialCipher(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption-keys");
    }

    @Test
    void invalidBase64_failsAtConstruction() {
        FileProperties props = new FileProperties();
        props.setEncryptionKeys(Map.of("default", "not!!base64@@@@"));
        props.setActiveKeyId("default");
        assertThatThrownBy(() -> new CredentialCipher(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void ciphertextWithoutKeyIdPrefix_throws() {
        CredentialCipher c = cipher();
        // 纯 Base64 无 keyId 前缀 → 拒绝
        assertThatThrownBy(() -> c.decrypt("c29tZWJhc2U2NGRhdGE="))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> {
                    FileException fe = (FileException) ex;
                    assertThat(fe.code()).isEqualTo(FileException.Code.CREDENTIAL_DECRYPT_ERROR);
                    assertThat(fe.getMessage()).contains("keyId 前缀");
                });
    }

    @Test
    void encryptOutputHasActiveKeyIdPrefix() {
        FileProperties props = new FileProperties();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("default", KEY_32_BYTES);
        keys.put("v2", KEY_32_BYTES_V2);
        props.setEncryptionKeys(keys);
        props.setActiveKeyId("v2");
        CredentialCipher c = new CredentialCipher(props);
        String enc = c.encrypt("hi");
        assertThat(enc).startsWith("v2:");
        assertThat(c.decrypt(enc)).isEqualTo("hi");
    }

    @Test
    void multipleKeys_decryptByRoutedKeyId() {
        // 同一 cipher 实例配两个 key，active=default 加密，但 v2 加密的密文也能解
        FileProperties props = new FileProperties();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("default", KEY_32_BYTES);
        keys.put("v2", KEY_32_BYTES_V2);
        props.setEncryptionKeys(keys);
        props.setActiveKeyId("default");
        CredentialCipher c = new CredentialCipher(props);

        String defaultEnc = c.encrypt("by-default");
        assertThat(defaultEnc).startsWith("default:");

        // 手工拼一个 v2: 前缀的密文 —— 用临时 cipher v2 active 生产
        FileProperties propsV2 = new FileProperties();
        propsV2.setEncryptionKeys(keys);
        propsV2.setActiveKeyId("v2");
        CredentialCipher v2 = new CredentialCipher(propsV2);
        String v2Enc = v2.encrypt("by-v2");
        assertThat(v2Enc).startsWith("v2:");

        // 同一 cipher 同时能解两种前缀
        assertThat(c.decrypt(defaultEnc)).isEqualTo("by-default");
        assertThat(c.decrypt(v2Enc)).isEqualTo("by-v2");
    }

    @Test
    void activeKeyIdMustBeInKeysMap() {
        FileProperties props = new FileProperties();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("default", KEY_32_BYTES);
        props.setEncryptionKeys(keys);
        props.setActiveKeyId("v999");
        assertThatThrownBy(() -> new CredentialCipher(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active-key-id")
                .hasMessageContaining("v999");
    }

    @Test
    void unknownKeyIdInCiphertext_throws() {
        CredentialCipher c = cipher();
        assertThatThrownBy(() -> c.decrypt("v99:somebase64data=="))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> {
                    FileException fe = (FileException) ex;
                    assertThat(fe.code()).isEqualTo(FileException.Code.CREDENTIAL_DECRYPT_ERROR);
                    assertThat(fe.getMessage()).contains("v99");
                });
    }

    @Test
    void rotation_oldCiphertextDecryptableAfterActiveKeyChange() {
        // 配两个 key，先 active=default 加密 → 切到 v2 active → 旧密文仍能解
        FileProperties props1 = new FileProperties();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("default", KEY_32_BYTES);
        keys.put("v2", KEY_32_BYTES_V2);
        props1.setEncryptionKeys(keys);
        props1.setActiveKeyId("default");
        CredentialCipher beforeRotation = new CredentialCipher(props1);
        String oldEnc = beforeRotation.encrypt("rotate-test");
        assertThat(oldEnc).startsWith("default:");

        FileProperties props2 = new FileProperties();
        props2.setEncryptionKeys(keys);
        props2.setActiveKeyId("v2");
        CredentialCipher afterRotation = new CredentialCipher(props2);

        // 轮转后旧密文仍可解
        assertThat(afterRotation.decrypt(oldEnc)).isEqualTo("rotate-test");
        // 新加密用 v2
        String newEnc = afterRotation.encrypt("new");
        assertThat(newEnc).startsWith("v2:");
    }

    @Test
    void emptyKeyIdInKeysMap_failsAtConstruction() {
        FileProperties props = new FileProperties();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("", KEY_32_BYTES);
        props.setEncryptionKeys(keys);
        props.setActiveKeyId("");
        assertThatThrownBy(() -> new CredentialCipher(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("空 keyId");
    }

    @Test
    void keyIdWithColon_failsAtConstruction() {
        FileProperties props = new FileProperties();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("bad:id", KEY_32_BYTES);
        props.setEncryptionKeys(keys);
        props.setActiveKeyId("bad:id");
        assertThatThrownBy(() -> new CredentialCipher(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("':'");
    }

    @Test
    void activeKeyIdMethodReturnsResolvedValue() {
        CredentialCipher c = cipher();
        assertThat(c.activeKeyId()).isEqualTo(FileProperties.DEFAULT_KEY_ID);
    }
}
