package com.wensaas.core.impl.file.storage;

import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.wensaas.core.impl.file.CredentialCipher;
import com.wensaas.core.impl.file.FileProperties;

class FileStorageFactoryTest {

    private static final String KEY = Base64.getEncoder().encodeToString(
            "test-aes-key-for-unit-only-32by!".getBytes());

    @Test
    void platformDefault_local(@TempDir Path tmp) {
        FileProperties props = newProps(tmp);
        FileStorageFactory factory = new FileStorageFactory(props,
                new CredentialCipher(props));
        FileStorage s = factory.platformDefault();
        assertThat(s).isSameAs(factory.localAdapter());
    }

    @Test
    void forConfig_localBackend_returnsLocalAdapter(@TempDir Path tmp) {
        FileProperties props = newProps(tmp);
        FileStorageFactory factory = new FileStorageFactory(props,
                new CredentialCipher(props));
        SysStorageConfig cfg = new SysStorageConfig();
        cfg.setId(1L);
        cfg.setBackendType("local");
        FileStorage s = factory.forConfig(cfg);
        assertThat(s).isSameAs(factory.localAdapter());
    }

    @Test
    void forConfig_invalidOssConfig_throws(@TempDir Path tmp) {
        FileProperties props = newProps(tmp);
        FileStorageFactory factory = new FileStorageFactory(props,
                new CredentialCipher(props));
        SysStorageConfig cfg = new SysStorageConfig();
        cfg.setId(1L);
        cfg.setBackendType("aliyun_oss");
        // 全空字段 → INVALID_BACKEND_CONFIG
        assertThatThrownBy(() -> factory.forConfig(cfg))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.INVALID_BACKEND_CONFIG));
    }

    @Test
    void forConfig_invalidCosConfig_throws(@TempDir Path tmp) {
        FileProperties props = newProps(tmp);
        FileStorageFactory factory = new FileStorageFactory(props,
                new CredentialCipher(props));
        SysStorageConfig cfg = new SysStorageConfig();
        cfg.setId(1L);
        cfg.setBackendType("tencent_cos");
        // 全空字段 → INVALID_BACKEND_CONFIG
        assertThatThrownBy(() -> factory.forConfig(cfg))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.INVALID_BACKEND_CONFIG));
    }

    @Test
    void evict_unknown_id_safe(@TempDir Path tmp) {
        FileProperties props = newProps(tmp);
        FileStorageFactory factory = new FileStorageFactory(props,
                new CredentialCipher(props));
        // 未缓存的 id evict 应当幂等
        factory.evict(999L);
    }

    private FileProperties newProps(Path tmp) {
        FileProperties props = new FileProperties();
        props.setEncryptionKeys(Map.of("default", KEY));
        props.setActiveKeyId("default");
        props.getLocal().setRoot(tmp.toString());
        return props;
    }
}
