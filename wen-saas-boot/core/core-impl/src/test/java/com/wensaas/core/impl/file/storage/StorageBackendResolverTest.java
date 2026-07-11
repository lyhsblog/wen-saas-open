package com.wensaas.core.impl.file.storage;

import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileStorage;
import com.wensaas.core.impl.file.adapter.AliyunOssFileStorageAdapter;
import com.wensaas.core.impl.file.adapter.LocalFileStorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.file.CredentialCipher;

class StorageBackendResolverTest {

    private SysStorageConfigMapper mapper;
    private CredentialCipher cipher;
    private FileStorageFactory factory;
    private LocalFileStorageAdapter local;
    private StorageBackendResolver resolver;

    @BeforeEach
    void setup() {
        mapper = mock(SysStorageConfigMapper.class);
        cipher = mock(CredentialCipher.class);
        factory = mock(FileStorageFactory.class);
        local = mock(LocalFileStorageAdapter.class);
        when(local.backendType()).thenReturn(FileBackendType.LOCAL);
        when(factory.platformDefault()).thenReturn(local);
        resolver = new StorageBackendResolver(mapper, cipher, factory);
    }

    @Test
    void resolveActive_noConfig_fallsBackToPlatformDefault() {
        when(mapper.selectActive(1L)).thenReturn(null);
        StorageBackendResolver.ResolveResult res = resolver.resolveActive(1L);
        assertThat(res.storage()).isSameAs(local);
        assertThat(res.config()).isNull();
        assertThat(res.backendConfigId()).isEqualTo(FileStorageFactory.PLATFORM_DEFAULT_CONFIG_ID);
        assertThat(res.pathPrefix()).isNull();
    }

    @Test
    void resolveActive_hasConfig_decryptsAndResolves() {
        SysStorageConfig cfg = new SysStorageConfig();
        cfg.setId(42L);
        cfg.setTenantId(1L);
        cfg.setBackendType("aliyun_oss");
        cfg.setEndpoint("oss-cn-hangzhou");
        cfg.setBucket("b");
        cfg.setAccessKeyId("ak");
        cfg.setAccessKeySecret("encrypted-secret");
        cfg.setPathPrefix("my-prefix");
        cfg.setStatus("active");
        when(mapper.selectActive(1L)).thenReturn(cfg);
        when(cipher.decrypt("encrypted-secret")).thenReturn("plain");
        AliyunOssFileStorageAdapter oss = mock(AliyunOssFileStorageAdapter.class);
        when(factory.forConfig(cfg)).thenReturn(oss);

        StorageBackendResolver.ResolveResult res = resolver.resolveActive(1L);
        assertThat(res.storage()).isSameAs(oss);
        assertThat(res.config().getAccessKeySecret()).isEqualTo("plain");
        assertThat(res.backendConfigId()).isEqualTo(42L);
        assertThat(res.pathPrefix()).isEqualTo("my-prefix");
    }

    @Test
    void resolveByConfigId_null_isPlatformDefault() {
        FileStorage s = resolver.resolveByConfigId(null);
        assertThat(s).isSameAs(local);
    }

    @Test
    void resolveByConfigId_zero_isPlatformDefault() {
        FileStorage s = resolver.resolveByConfigId(FileStorageFactory.PLATFORM_DEFAULT_CONFIG_ID);
        assertThat(s).isSameAs(local);
    }

    @Test
    void resolveByConfigId_archived_stillWorks() {
        SysStorageConfig archived = new SysStorageConfig();
        archived.setId(99L);
        archived.setTenantId(1L);
        archived.setBackendType("aliyun_oss");
        archived.setEndpoint("oss");
        archived.setBucket("b");
        archived.setAccessKeyId("ak");
        archived.setAccessKeySecret("enc");
        archived.setStatus("archived");
        when(mapper.selectByIdAnyStatus(99L)).thenReturn(archived);
        when(cipher.decrypt("enc")).thenReturn("plain");
        AliyunOssFileStorageAdapter oss = mock(AliyunOssFileStorageAdapter.class);
        when(factory.forConfig(archived)).thenReturn(oss);

        FileStorage resolved = resolver.resolveByConfigId(99L);
        assertThat(resolved).isSameAs(oss);
    }

    @Test
    void resolveByConfigId_missing_fallsBackToPlatformDefault() {
        when(mapper.selectByIdAnyStatus(404L)).thenReturn(null);
        FileStorage s = resolver.resolveByConfigId(404L);
        assertThat(s).isSameAs(local);
    }
}
