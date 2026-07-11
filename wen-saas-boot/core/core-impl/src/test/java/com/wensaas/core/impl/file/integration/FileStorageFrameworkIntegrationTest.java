package com.wensaas.core.impl.file.integration;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.FilePermissionChecker;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.file.FileProperties;
import com.wensaas.core.impl.file.storage.FileStorageFactory;
import com.wensaas.core.impl.file.SysFile;
import com.wensaas.core.impl.file.SysFileMapper;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件存储框架端到端集成测试（真 PostgreSQL + 真 Redis + LocalAdapter 真磁盘）。
 *
 * <p>覆盖：uploadDirect / requestSignedUpload+confirmUpload / signedDownloadUrl 缓存 /
 * 跨租户隔离 / softDelete / FilePermissionChecker 拒绝 / metadata + listByOwner。
 *
 * <p>不跑 AliyunOss 真集成（需账号 + CI 风险）；由单元 mock OssClient 覆盖。
 */
@SpringBootTest(classes = {TestApp.class, FileStorageFrameworkIntegrationTest.TestPermissionConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileStorageFrameworkIntegrationTest {

    private static final long TENANT_A = 9851L;
    private static final long TENANT_B = 9852L;
    private static final long USER_ID = 33L;
    private static final String CRM_LOCKED = "crm-locked";

    @Autowired JdbcTemplate jdbc;
    @Autowired FileService fileService;
    @Autowired SysFileMapper fileMapper;
    @Autowired FileProperties fileProperties;
    @Autowired StringRedisTemplate redis;

    @BeforeAll
    void seed() {
        // 表结构由单一基线迁移 V1.0.0 保证（测试连已迁移的 wen_saas 库，flyway.enabled=false）。
        cleanup();
    }

    @AfterAll
    void teardown() {
        cleanup();
        TenantContext.clear();
        UserContext.clear();
    }

    @BeforeEach
    void setupContext() {
        TenantContext.set(TENANT_A);
        UserContext.set(new UserPrincipal(USER_ID, TENANT_A, "alice", "sess", false, null, false, null));
        cleanup();
    }

    @AfterEach
    void clearAfter() {
        cleanup();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_file WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbc.update("DELETE FROM sys_storage_config WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        // 清本地磁盘对应文件夹
        try {
            Path root = Paths.get(fileProperties.getLocal().getRoot());
            if (Files.exists(root.resolve("tenants/" + TENANT_A))) {
                deleteRecursive(root.resolve("tenants/" + TENANT_A));
            }
            if (Files.exists(root.resolve("tenants/" + TENANT_B))) {
                deleteRecursive(root.resolve("tenants/" + TENANT_B));
            }
        } catch (Exception ignore) {
            /* ignore */
        }
    }

    private void deleteRecursive(Path p) throws Exception {
        if (!Files.exists(p)) return;
        if (Files.isDirectory(p)) {
            try (var s = Files.list(p)) {
                s.forEach(this::silentDelete);
            }
        }
        Files.deleteIfExists(p);
    }

    private void silentDelete(Path p) {
        try {
            deleteRecursive(p);
        } catch (Exception ignore) { /* ignore */ }
    }

    @Test
    void uploadDirect_then_download_roundTrip() throws Exception {
        byte[] payload = ("hello-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        FileUploadResult res = fileService.uploadDirect(new FileUploadRequest(
                "a.txt", "text/plain", payload.length, new ByteArrayInputStream(payload),
                FileVisibility.FILE_CENTER, null, null, null, "/my"));
        assertThat(res.fileId()).isNotBlank();

        // 真磁盘验证
        FileMetadata md = fileService.metadata(res.fileId());
        Path objectPath = Paths.get(fileProperties.getLocal().getRoot()).resolve(md.backendObjectKey());
        assertThat(Files.exists(objectPath)).isTrue();
        assertThat(Files.readAllBytes(objectPath)).isEqualTo(payload);
    }

    @Test
    void signedDownloadUrl_cachesInRedis() {
        FileUploadResult res = fileService.uploadDirect(simpleUpload("x"));
        String first = fileService.signedDownloadUrl(res.fileId(), Duration.ofMinutes(5));
        // Redis 应有缓存
        String cached = redis.opsForValue().get("wen-saas-boot:file:url:" + res.fileId());
        assertThat(cached).isEqualTo(first);
        String second = fileService.signedDownloadUrl(res.fileId(), Duration.ofMinutes(5));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void requestSignedUpload_then_confirm_flow() throws Exception {
        var signed = fileService.requestSignedUpload(new com.wensaas.core.api.file.SignedUploadRequest(
                "a.txt", "text/plain", 5, FileVisibility.FILE_CENTER, null, null, null, null));
        // 模拟客户端 PUT：直接往 LocalAdapter 后端写
        FileMetadata md = fileService.metadata(signed.fileId());
        assertThat(md.active()).isFalse();
        Path objectPath = Paths.get(fileProperties.getLocal().getRoot()).resolve(md.backendObjectKey());
        Files.createDirectories(objectPath.getParent());
        Files.writeString(objectPath, "12345");
        // confirm
        var confirmed = fileService.confirmUpload(signed.fileId());
        assertThat(confirmed.sizeBytes()).isEqualTo(5);
        assertThat(fileService.metadata(signed.fileId()).active()).isTrue();
    }

    @Test
    void crossTenantIsolation() {
        FileUploadResult resA = fileService.uploadDirect(simpleUpload("A"));
        // 切到 tenant B
        UserContext.set(new UserPrincipal(USER_ID, TENANT_B, "bob", "sess", false, null, false, null));
        TenantContext.set(TENANT_B);
        assertThatThrownBy(() -> fileService.metadata(resA.fileId()))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.FILE_NOT_FOUND));
    }

    @Test
    void softDelete_makesMetadataInvisible() {
        FileUploadResult res = fileService.uploadDirect(simpleUpload("D"));
        fileService.delete(res.fileId());
        assertThatThrownBy(() -> fileService.metadata(res.fileId()))
                .isInstanceOf(FileException.class);
    }

    @Test
    void permissionChecker_denyBusinessOnlyDownload() {
        FileUploadResult res = fileService.uploadDirect(new FileUploadRequest(
                "secret.txt", "text/plain", 3,
                new ByteArrayInputStream("hey".getBytes()),
                FileVisibility.BUSINESS_ONLY, CRM_LOCKED, "客户", 1L, null));
        assertThatThrownBy(() -> fileService.signedDownloadUrl(res.fileId(), null))
                .isInstanceOf(FileException.class)
                .satisfies(ex -> assertThat(((FileException) ex).code())
                        .isEqualTo(FileException.Code.DOWNLOAD_FORBIDDEN));
    }

    @Test
    void listByOwner_returnsActive() {
        fileService.uploadDirect(new FileUploadRequest(
                "a.txt", "text/plain", 1, new ByteArrayInputStream(new byte[]{1}),
                FileVisibility.BUSINESS_ONLY, "crm", "客户", 7L, null));
        fileService.uploadDirect(new FileUploadRequest(
                "b.txt", "text/plain", 1, new ByteArrayInputStream(new byte[]{1}),
                FileVisibility.BUSINESS_ONLY, "crm", "客户", 7L, null));
        List<FileMetadata> list = fileService.listByOwner("crm", "客户", 7L);
        assertThat(list).hasSize(2);
    }

    private FileUploadRequest simpleUpload(String label) {
        byte[] payload = label.getBytes();
        return new FileUploadRequest(
                "a-" + label + ".txt", "text/plain", payload.length,
                new ByteArrayInputStream(payload),
                FileVisibility.FILE_CENTER, null, null, null, "/my");
    }

    /** 注入一个拒绝 owner_module = crm-locked 的 checker。 */
    @Configuration
    static class TestPermissionConfig {
        @Bean FilePermissionChecker denyCrmLocked() {
            return new FilePermissionChecker() {
                @Override public String ownerModule() { return CRM_LOCKED; }
                @Override public boolean canDownload(FileMetadata file, Long currentUserId) {
                    return false;
                }
            };
        }
    }
}
