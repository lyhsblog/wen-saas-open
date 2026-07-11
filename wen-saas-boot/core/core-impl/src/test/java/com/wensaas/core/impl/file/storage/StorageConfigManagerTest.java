package com.wensaas.core.impl.file.storage;

import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.file.adapter.LocalFileStorageAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.file.CredentialCipher;

class StorageConfigManagerTest {

    private static final long TENANT = 9801L;
    private SysStorageConfigMapper mapper;
    private CredentialCipher cipher;
    private FileStorageFactory factory;
    private AuditLogRecorder audit;
    private StorageConfigManager manager;

    @BeforeEach
    void setup() {
        mapper = mock(SysStorageConfigMapper.class);
        cipher = mock(CredentialCipher.class);
        factory = mock(FileStorageFactory.class);
        audit = mock(AuditLogRecorder.class);
        manager = new StorageConfigManager(mapper, cipher, factory, audit);
        when(cipher.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listForTenant_masksSecret() {
        SysStorageConfig active = active(1L);
        SysStorageConfig archived = newConfig(2L, "archived", "raw-secret-2");
        when(mapper.selectAllForTenant(TENANT)).thenReturn(List.of(active, archived));
        List<SysStorageConfig> list = manager.listForTenant();
        assertThat(list).allSatisfy(c -> assertThat(c.getAccessKeySecret()).isEqualTo("********"));
    }

    @Test
    void getActive_noActive_returnsNull() {
        when(mapper.selectActive(TENANT)).thenReturn(null);
        assertThat(manager.getActive()).isNull();
    }

    @Test
    void getActive_masksSecret() {
        when(mapper.selectActive(TENANT)).thenReturn(active(1L));
        SysStorageConfig a = manager.getActive();
        assertThat(a.getAccessKeySecret()).isEqualTo("********");
    }

    @Test
    void testConnection_localBackend_noOp() {
        manager.testConnection(new StorageConfigManager.StorageConfigDraft(
                FileBackendType.LOCAL, null, null, null, null, null));
        verify(audit, never()).recordCreate(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void switchToCustomOss_localDraft_archivesAndInserts() {
        when(mapper.selectActive(TENANT)).thenReturn(null);
        StorageConfigManager.StorageConfigDraft draft = new StorageConfigManager.StorageConfigDraft(
                FileBackendType.LOCAL, null, null, null, null, "prefix");

        SysStorageConfig saved = manager.switchToCustomOss(draft);

        verify(mapper).archiveAllActive(TENANT);
        ArgumentCaptor<SysStorageConfig> cap = ArgumentCaptor.forClass(SysStorageConfig.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("active");
        assertThat(cap.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(cap.getValue().getBackendType()).isEqualTo("local");
        assertThat(saved.getStatus()).isEqualTo("active");
        verify(audit).recordCreate(eq("sys.storage"), eq("storage_config"), any(), eq("切换到自有对象存储"), any());
    }

    @Test
    void switchToCustomOss_priorActive_evictsFactory() {
        SysStorageConfig prior = active(77L);
        when(mapper.selectActive(TENANT)).thenReturn(prior);
        StorageConfigManager.StorageConfigDraft draft = new StorageConfigManager.StorageConfigDraft(
                FileBackendType.LOCAL, null, null, null, null, null);
        manager.switchToCustomOss(draft);
        verify(factory).evict(77L);
    }

    @Test
    void switchToCustomOss_encryptsSecret() {
        when(mapper.selectActive(TENANT)).thenReturn(null);
        StorageConfigManager.StorageConfigDraft draft = new StorageConfigManager.StorageConfigDraft(
                FileBackendType.ALIYUN_OSS, "oss-cn", "b", "ak", "plain-secret", null);
        // testConnection 会调 OSSClientBuilder build 真发请求 → 抛错；测试这里只关注 ALIYUN 字段校验
        // 用反射子类替换 testConnection？最简洁做法：用 LOCAL 跑加密路径 + 单独验证 cipher.encrypt 被调
        StorageConfigManager.StorageConfigDraft localDraft = new StorageConfigManager.StorageConfigDraft(
                FileBackendType.LOCAL, null, null, null, "plain-secret", null);
        when(mapper.selectActive(TENANT)).thenReturn(null);
        manager.switchToCustomOss(localDraft);
        ArgumentCaptor<SysStorageConfig> cap = ArgumentCaptor.forClass(SysStorageConfig.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().getAccessKeySecret()).isEqualTo("enc:plain-secret");
    }

    @Test
    void probe_localBackend_uploadsAndDeletes_noResidue(@TempDir Path tempDir) throws Exception {
        LocalFileStorageAdapter local = new LocalFileStorageAdapter(tempDir, new byte[32]);
        when(factory.localAdapter()).thenReturn(local);

        String err = manager.probe(new StorageConfigManager.StorageConfigDraft(
                FileBackendType.LOCAL, null, null, null, null, null));

        assertThat(err).isNull();
        try (var files = java.nio.file.Files.list(tempDir)) {
            assertThat(files).isEmpty(); // 测试对象已删，无残留
        }
    }

    @Test
    void probe_localBackendFailure_returnsMessage() {
        LocalFileStorageAdapter broken = mock(LocalFileStorageAdapter.class);
        org.mockito.Mockito.doThrow(new FileException(FileException.Code.BACKEND_IO_ERROR, "磁盘只读"))
                .when(broken).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        when(factory.localAdapter()).thenReturn(broken);

        String err = manager.probe(new StorageConfigManager.StorageConfigDraft(
                FileBackendType.LOCAL, null, null, null, null, null));

        assertThat(err).contains("本地存储").contains("磁盘只读");
    }

    @Test
    void switchToCustomOss_maskedSecret_reusesStoredCredential() {
        SysStorageConfig prior = newConfig(9L, "active", "enc:raw-secret");
        when(mapper.selectActive(TENANT)).thenReturn(prior);
        when(cipher.decrypt("enc:raw-secret")).thenReturn("raw-secret");
        // LOCAL 跳过 testConnection 的真实探活，聚焦掩码替换 + 加密落库路径
        StorageConfigManager.StorageConfigDraft draft = new StorageConfigManager.StorageConfigDraft(
                FileBackendType.LOCAL, null, null, null, StorageConfigManager.SECRET_MASK, null);

        manager.switchToCustomOss(draft);

        ArgumentCaptor<SysStorageConfig> cap = ArgumentCaptor.forClass(SysStorageConfig.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().getAccessKeySecret()).isEqualTo("enc:raw-secret");
    }

    @Test
    void testConnection_maskedSecret_withoutStoredConfig_throws() {
        when(mapper.selectActive(TENANT)).thenReturn(null);
        StorageConfigManager.StorageConfigDraft draft = new StorageConfigManager.StorageConfigDraft(
                FileBackendType.ALIYUN_OSS, "oss-cn-hangzhou.aliyuncs.com", "b", "ak",
                StorageConfigManager.SECRET_MASK, null);

        assertThatThrownBy(() -> manager.testConnection(draft))
                .isInstanceOf(FileException.class)
                .hasMessageContaining("没有已保存的凭证");
    }

    @Test
    void switchToPlatform_noActive_noOp() {
        when(mapper.selectActive(TENANT)).thenReturn(null);
        manager.switchToPlatform();
        verify(mapper, never()).archiveAllActive(any());
        verify(factory, never()).evict(any());
        verify(audit, never()).recordCreate(any(), any(), any(), any(), any());
    }

    @Test
    void switchToPlatform_withActive_archivesAndAudits() {
        SysStorageConfig prior = active(55L);
        when(mapper.selectActive(TENANT)).thenReturn(prior);
        manager.switchToPlatform();
        verify(mapper).archiveAllActive(TENANT);
        verify(factory).evict(55L);
        verify(audit).recordCreate(eq("sys.storage"), eq("storage_config"), any(),
                eq("切换回平台默认存储"), any());
    }

    private SysStorageConfig active(long id) {
        return newConfig(id, "active", "raw-secret");
    }

    private SysStorageConfig newConfig(long id, String status, String secret) {
        SysStorageConfig c = new SysStorageConfig();
        c.setId(id);
        c.setTenantId(TENANT);
        c.setBackendType("aliyun_oss");
        c.setEndpoint("oss-cn-hangzhou");
        c.setBucket("b");
        c.setAccessKeyId("ak");
        c.setAccessKeySecret(secret);
        c.setStatus(status);
        return c;
    }
}
