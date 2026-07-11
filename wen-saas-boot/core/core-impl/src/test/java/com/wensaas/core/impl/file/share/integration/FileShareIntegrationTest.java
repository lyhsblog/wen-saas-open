package com.wensaas.core.impl.file.share.integration;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.file.share.FileShareConsumeResult;
import com.wensaas.core.api.file.share.FileShareCreateRequest;
import com.wensaas.core.api.file.share.FileShareInfo;
import com.wensaas.core.api.file.share.FileShareService;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.file.FileProperties;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 文件分享 v2 集成测试（真 PG + Redis + LocalAdapter 真磁盘）。
 *
 * <p>覆盖：创建分享 + 查看 + 消费签名 URL + 密码错 + 次数限制 + 撤销 + 跨租户 NOT_FOUND。
 */
@SpringBootTest(classes = TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileShareIntegrationTest {

    private static final long TENANT_A = 9871L;
    private static final long TENANT_B = 9872L;
    private static final long USER_ID = 71L;

    @Autowired JdbcTemplate jdbc;
    @Autowired FileService fileService;
    @Autowired FileShareService shareService;
    @Autowired FileProperties fileProperties;

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
    void clearAfter() { cleanup(); }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_file_share WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbc.update("DELETE FROM sys_file WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        try {
            Path root = Paths.get(fileProperties.getLocal().getRoot());
            deleteRecursive(root.resolve("tenants/" + TENANT_A));
            deleteRecursive(root.resolve("tenants/" + TENANT_B));
        } catch (Exception ignore) { /* ignore */ }
    }

    private void deleteRecursive(Path p) throws Exception {
        if (!Files.exists(p)) return;
        if (Files.isDirectory(p)) {
            try (var s = Files.list(p)) { s.forEach(this::silentDelete); }
        }
        Files.deleteIfExists(p);
    }

    private void silentDelete(Path p) {
        try { deleteRecursive(p); } catch (Exception ignore) { /* ignore */ }
    }

    private FileUploadResult uploadSample(String name) {
        byte[] payload = ("share-" + name).getBytes(StandardCharsets.UTF_8);
        return fileService.uploadDirect(new FileUploadRequest(
                name, "text/plain", payload.length, new ByteArrayInputStream(payload),
                FileVisibility.FILE_CENTER, null, null, null, null));
    }

    @Test
    void createShare_thenGet_andConsume() {
        FileUploadResult file = uploadSample("a.txt");

        FileShareInfo info = shareService.createShare(new FileShareCreateRequest(
                file.fileId(), Duration.ofDays(7), null, 5));
        assertThat(info.shareToken()).isNotBlank();
        assertThat(info.hasPassword()).isFalse();
        assertThat(info.remainingDownloads()).isEqualTo(5);

        FileShareInfo fetched = shareService.getShare(info.shareToken());
        assertThat(fetched.fileId()).isEqualTo(file.fileId());
        assertThat(fetched.downloadCount()).isZero();

        FileShareConsumeResult result = shareService.consumeShare(info.shareToken(), null);
        assertThat(result.signedDownloadUrl()).contains("/api/v1/files/local-download");
        // 计数自增
        FileShareInfo afterConsume = shareService.getShare(info.shareToken());
        assertThat(afterConsume.downloadCount()).isEqualTo(1);
        assertThat(afterConsume.remainingDownloads()).isEqualTo(4);
    }

    @Test
    void consumeShare_withPassword() {
        FileUploadResult file = uploadSample("pwd.txt");
        FileShareInfo info = shareService.createShare(new FileShareCreateRequest(
                file.fileId(), null, "1234", null));
        assertThat(info.hasPassword()).isTrue();

        assertThatThrownBy(() -> shareService.consumeShare(info.shareToken(), null))
                .isInstanceOf(FileException.class)
                .matches(ex -> ((FileException) ex).code()
                        == FileException.Code.SHARE_PASSWORD_REQUIRED);

        assertThatThrownBy(() -> shareService.consumeShare(info.shareToken(), "9999"))
                .isInstanceOf(FileException.class)
                .matches(ex -> ((FileException) ex).code()
                        == FileException.Code.SHARE_PASSWORD_WRONG);

        FileShareConsumeResult ok = shareService.consumeShare(info.shareToken(), "1234");
        assertThat(ok.signedDownloadUrl()).isNotBlank();
    }

    @Test
    void downloadLimitEnforced() {
        FileUploadResult file = uploadSample("limit.txt");
        FileShareInfo info = shareService.createShare(new FileShareCreateRequest(
                file.fileId(), null, null, 2));

        shareService.consumeShare(info.shareToken(), null);
        shareService.consumeShare(info.shareToken(), null);
        // 第 3 次超限
        assertThatThrownBy(() -> shareService.consumeShare(info.shareToken(), null))
                .isInstanceOf(FileException.class)
                .matches(ex -> ((FileException) ex).code()
                        == FileException.Code.SHARE_DOWNLOAD_LIMIT_EXCEEDED);
    }

    @Test
    void revoke_thenAccess_throws() {
        FileUploadResult file = uploadSample("rev.txt");
        FileShareInfo info = shareService.createShare(new FileShareCreateRequest(
                file.fileId(), null, null, null));

        shareService.revokeShare(info.shareToken());

        // revoke 软删 + 标记 status，二次查找按 NOT_FOUND（@TableLogic 过滤）
        assertThatThrownBy(() -> shareService.getShare(info.shareToken()))
                .isInstanceOf(FileException.class)
                .matches(ex -> ((FileException) ex).code() == FileException.Code.SHARE_NOT_FOUND);
    }

    @Test
    void crossTenantAccess_throwsAsNotFound() {
        FileUploadResult file = uploadSample("cross.txt");
        FileShareInfo info = shareService.createShare(new FileShareCreateRequest(
                file.fileId(), null, null, null));

        // 切到另一个租户
        TenantContext.set(TENANT_B);
        assertThatThrownBy(() -> shareService.getShare(info.shareToken()))
                .isInstanceOf(FileException.class)
                .matches(ex -> ((FileException) ex).code() == FileException.Code.SHARE_NOT_FOUND);
        TenantContext.set(TENANT_A);
    }

    @Test
    void listShares_returnsActiveOnly() {
        FileUploadResult file = uploadSample("list.txt");
        shareService.createShare(new FileShareCreateRequest(file.fileId(), null, null, null));
        FileShareInfo toRevoke = shareService.createShare(
                new FileShareCreateRequest(file.fileId(), null, null, null));
        shareService.revokeShare(toRevoke.shareToken());

        List<FileShareInfo> list = shareService.listShares(file.fileId());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).status()).isEqualTo("active");
    }
}
