package com.wensaas.core.impl.file.share;

import com.wensaas.core.api.file.FileBackendType;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.file.share.FileShareConsumeResult;
import com.wensaas.core.api.file.share.FileShareCreateRequest;
import com.wensaas.core.api.file.share.FileShareInfo;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.password.PasswordHasher;
import com.wensaas.core.impl.file.FileProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
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

class FileShareServiceImplTest {

    private static final Long TENANT_A = 9601L;
    private static final Long TENANT_B = 9602L;
    private static final String FILE_ID = "f-abc";
    private static final String SHARE_TOKEN = "tok-abc";

    private SysFileShareMapper mapper;
    private FileService fileService;
    private PasswordHasher passwordHasher;
    private FileProperties props;
    private FileShareServiceImpl service;

    @BeforeEach
    void setup() {
        mapper = mock(SysFileShareMapper.class);
        fileService = mock(FileService.class);
        passwordHasher = mock(PasswordHasher.class);
        props = new FileProperties();
        props.setSignedUrlTtl(Duration.ofMinutes(5));
        service = new FileShareServiceImpl(mapper, fileService, passwordHasher, props);
        TenantContext.set(TENANT_A);

        FileMetadata file = stubFile();
        when(fileService.metadata(FILE_ID)).thenReturn(file);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void createShare_withPassword_hashesAndPersists() {
        when(passwordHasher.hash("1234")).thenReturn("$bcrypt$");
        FileShareCreateRequest req = new FileShareCreateRequest(
                FILE_ID, Duration.ofDays(7), "1234", 5);

        FileShareInfo info = service.createShare(req);

        ArgumentCaptor<SysFileShare> cap = ArgumentCaptor.forClass(SysFileShare.class);
        verify(mapper).insert((SysFileShare) cap.capture());
        SysFileShare saved = cap.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
        assertThat(saved.getFileId()).isEqualTo(FILE_ID);
        assertThat(saved.getShareToken()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(saved.getPasswordHash()).isEqualTo("$bcrypt$");
        assertThat(saved.getMaxDownloads()).isEqualTo(5);
        assertThat(saved.getDownloadCount()).isZero();
        assertThat(saved.getStatus()).isEqualTo("active");

        assertThat(info.hasPassword()).isTrue();
        assertThat(info.remainingDownloads()).isEqualTo(5);
    }

    @Test
    void createShare_withoutPassword_neverHash() {
        FileShareCreateRequest req = new FileShareCreateRequest(
                FILE_ID, null, null, null);
        FileShareInfo info = service.createShare(req);
        verify(passwordHasher, never()).hash(any());
        assertThat(info.hasPassword()).isFalse();
        assertThat(info.remainingDownloads()).isNull();
        assertThat(info.expiresAt()).isNull();
    }

    @Test
    void createShare_invalidPasswordLength_failsAtRequest() {
        // record 紧凑构造器
        assertThatThrownBy(() ->
                new FileShareCreateRequest(FILE_ID, null, "1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4-6");
        assertThatThrownBy(() ->
                new FileShareCreateRequest(FILE_ID, null, "1234567", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getShare_notFound_throws() {
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(null);
        assertThatThrownBy(() -> service.getShare(SHARE_TOKEN))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_NOT_FOUND));
    }

    @Test
    void getShare_crossTenant_throwsAsNotFound() {
        SysFileShare entity = activeEntity();
        entity.setTenantId(TENANT_B);          // 另一个租户
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        assertThatThrownBy(() -> service.getShare(SHARE_TOKEN))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_NOT_FOUND));
    }

    @Test
    void getShare_revoked_throws() {
        SysFileShare entity = activeEntity();
        entity.setStatus("revoked");
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        assertThatThrownBy(() -> service.getShare(SHARE_TOKEN))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_REVOKED));
    }

    @Test
    void getShare_expired_throws() {
        SysFileShare entity = activeEntity();
        entity.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        assertThatThrownBy(() -> service.getShare(SHARE_TOKEN))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_EXPIRED));
    }

    @Test
    void consumeShare_passwordRequiredButMissing_throws() {
        SysFileShare entity = activeEntity();
        entity.setPasswordHash("$bcrypt$");
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        assertThatThrownBy(() -> service.consumeShare(SHARE_TOKEN, null))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_PASSWORD_REQUIRED));
        assertThatThrownBy(() -> service.consumeShare(SHARE_TOKEN, ""))
                .isInstanceOf(FileException.class);
        verify(mapper, never()).tryConsume(anyString(), any());
    }

    @Test
    void consumeShare_passwordWrong_throws() {
        SysFileShare entity = activeEntity();
        entity.setPasswordHash("$bcrypt$");
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        when(passwordHasher.verify("WRONG", "$bcrypt$")).thenReturn(false);
        assertThatThrownBy(() -> service.consumeShare(SHARE_TOKEN, "WRONG"))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_PASSWORD_WRONG));
        verify(mapper, never()).tryConsume(anyString(), any());
    }

    @Test
    void consumeShare_success_atomicConsumeAndReturnUrl() {
        SysFileShare entity = activeEntity();
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        when(mapper.tryConsume(eq(SHARE_TOKEN), any())).thenReturn(1);
        when(fileService.signedDownloadUrl(eq(FILE_ID), any(Duration.class)))
                .thenReturn("https://download/abc");

        FileShareConsumeResult result = service.consumeShare(SHARE_TOKEN, null);

        assertThat(result.signedDownloadUrl()).isEqualTo("https://download/abc");
        assertThat(result.expiresAt()).isAfter(java.time.Instant.now());
        verify(mapper).tryConsume(eq(SHARE_TOKEN), any());
    }

    @Test
    void consumeShare_affectedZero_thenExceeded_throws() {
        SysFileShare entity = activeEntity();
        entity.setMaxDownloads(3);
        entity.setDownloadCount(3);          // 已满
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        when(mapper.tryConsume(eq(SHARE_TOKEN), any())).thenReturn(0);

        assertThatThrownBy(() -> service.consumeShare(SHARE_TOKEN, null))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_DOWNLOAD_LIMIT_EXCEEDED));
    }

    @Test
    void consumeShare_affectedZero_dueToConcurrentRevoke_returnsRevoked() {
        SysFileShare entity = activeEntity();
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        when(mapper.tryConsume(eq(SHARE_TOKEN), any())).thenReturn(0);
        // 第二次重读：已被并发撤销
        SysFileShare revoked = activeEntity();
        revoked.setStatus("revoked");
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity, revoked);

        assertThatThrownBy(() -> service.consumeShare(SHARE_TOKEN, null))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_REVOKED));
    }

    @Test
    void revokeShare_setsStatusAndSoftDeletes() {
        SysFileShare entity = activeEntity();
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);

        service.revokeShare(SHARE_TOKEN);

        verify(mapper).updateById((SysFileShare) any(SysFileShare.class));
        verify(mapper).deleteById(eq(entity.getId()));
        assertThat(entity.getStatus()).isEqualTo("revoked");
    }

    @Test
    void revokeShare_crossTenant_throwsAsNotFound() {
        SysFileShare entity = activeEntity();
        entity.setTenantId(TENANT_B);
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        assertThatThrownBy(() -> service.revokeShare(SHARE_TOKEN))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_NOT_FOUND));
    }

    @Test
    void listShares_returnsMappedInfo() {
        SysFileShare entity = activeEntity();
        when(mapper.selectAliveByFileId(TENANT_A, FILE_ID)).thenReturn(List.of(entity));
        List<FileShareInfo> result = service.listShares(FILE_ID);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).shareToken()).isEqualTo(SHARE_TOKEN);
        assertThat(result.get(0).fileName()).isEqualTo("hello.txt");
    }

    // ===================== 公开（免登录）路径 FC-901 =====================

    @Test
    void getPublicShare_noTenantContext_returnsInfo() {
        TenantContext.clear();                 // 模拟匿名访客：无登录态 / 无租户上下文
        SysFileShare entity = activeEntity();
        entity.setTenantId(TENANT_B);          // 任意租户的 token 都可读（不做租户一致校验）
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);

        FileShareInfo info = service.getPublicShare(SHARE_TOKEN);

        assertThat(info.shareToken()).isEqualTo(SHARE_TOKEN);
        assertThat(info.fileName()).isEqualTo("hello.txt");
    }

    @Test
    void getPublicShare_notFound_throws() {
        TenantContext.clear();
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(null);
        assertThatThrownBy(() -> service.getPublicShare(SHARE_TOKEN))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_NOT_FOUND));
    }

    @Test
    void getPublicShare_expired_throws() {
        TenantContext.clear();
        SysFileShare entity = activeEntity();
        entity.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        assertThatThrownBy(() -> service.getPublicShare(SHARE_TOKEN))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_EXPIRED));
    }

    @Test
    void consumePublicShare_success_returnsUrl() {
        TenantContext.clear();
        SysFileShare entity = activeEntity();
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        when(mapper.tryConsume(eq(SHARE_TOKEN), any())).thenReturn(1);
        when(fileService.signedDownloadUrl(eq(FILE_ID), any(Duration.class)))
                .thenReturn("https://download/pub");

        FileShareConsumeResult result = service.consumePublicShare(SHARE_TOKEN, null);

        assertThat(result.signedDownloadUrl()).isEqualTo("https://download/pub");
        verify(mapper).tryConsume(eq(SHARE_TOKEN), any());
    }

    @Test
    void consumePublicShare_passwordRequiredButMissing_throws() {
        TenantContext.clear();
        SysFileShare entity = activeEntity();
        entity.setPasswordHash("$bcrypt$");
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        assertThatThrownBy(() -> service.consumePublicShare(SHARE_TOKEN, null))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_PASSWORD_REQUIRED));
        verify(mapper, never()).tryConsume(anyString(), any());
    }

    @Test
    void consumePublicShare_limitExceeded_throws() {
        TenantContext.clear();
        SysFileShare entity = activeEntity();
        entity.setMaxDownloads(3);
        entity.setDownloadCount(3);
        when(mapper.selectByToken(SHARE_TOKEN)).thenReturn(entity);
        when(mapper.tryConsume(eq(SHARE_TOKEN), any())).thenReturn(0);
        assertThatThrownBy(() -> service.consumePublicShare(SHARE_TOKEN, null))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.SHARE_DOWNLOAD_LIMIT_EXCEEDED));
    }

    // ============================== fixtures ==============================

    private SysFileShare activeEntity() {
        SysFileShare e = new SysFileShare();
        e.setId(1234L);
        e.setTenantId(TENANT_A);
        e.setShareToken(SHARE_TOKEN);
        e.setFileId(FILE_ID);
        e.setStatus("active");
        e.setDownloadCount(0);
        e.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        e.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        return e;
    }

    private FileMetadata stubFile() {
        return new FileMetadata(
                FILE_ID, TENANT_A, "hello.txt", "text/plain", 11L,
                FileVisibility.FILE_CENTER, null, null, null, null,
                FileBackendType.LOCAL, null,
                "tenants/" + TENANT_A + "/202605/" + FILE_ID,
                "active", 1L, LocalDateTime.now(), null);
    }
}
