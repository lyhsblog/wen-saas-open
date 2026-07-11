package com.wensaas.core.impl.file;

import com.wensaas.core.api.file.FileException;
import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.SignedUploadRequest;
import com.wensaas.core.api.file.SignedUploadUrl;
import com.wensaas.core.api.file.center.FileCenterException;
import com.wensaas.core.api.file.center.FileCenterListView;
import com.wensaas.core.api.file.center.FileCenterService;
import com.wensaas.core.api.file.center.FolderView;
import com.wensaas.core.api.file.center.QuotaView;
import com.wensaas.core.api.file.center.RecycleListView;
import com.wensaas.core.api.file.share.FileShareConsumeResult;
import com.wensaas.core.api.file.share.FileShareCreateRequest;
import com.wensaas.core.api.file.share.FileShareInfo;
import com.wensaas.core.api.file.share.FileShareService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * {@code wen-saas-boot.file.enabled=false} 时兜底装配：注入"全部抛 {@link FileException}"实现，
 * 让任何强依赖 {@link FileService} / {@link FileShareService} 的业务点启动时有 Bean 可注入，
 * 但调用即报错。
 *
 * <p>与 {@code NoopAuditConfiguration} 同模式。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wen-saas-boot.file", name = "enabled",
        havingValue = "false")
public class NoopFileConfiguration {

    @Bean
    @ConditionalOnMissingBean(FileService.class)
    public FileService noopFileService() {
        return new NoopFileService();
    }

    @Bean
    @ConditionalOnMissingBean(FileShareService.class)
    public FileShareService noopFileShareService() {
        return new NoopFileShareService();
    }

    @Bean
    @ConditionalOnMissingBean(FileCenterService.class)
    public FileCenterService noopFileCenterService() {
        return new NoopFileCenterService();
    }

    static final class NoopFileService implements FileService {
        @Override public FileUploadResult uploadDirect(FileUploadRequest request) {
            throw disabled();
        }
        @Override public SignedUploadUrl requestSignedUpload(SignedUploadRequest request) {
            throw disabled();
        }
        @Override public FileUploadResult confirmUpload(String fileId) {
            throw disabled();
        }
        @Override public String signedDownloadUrl(String fileId, Duration validity) {
            throw disabled();
        }
        @Override public byte[] readContent(String fileId) {
            throw disabled();
        }
        @Override public void delete(String fileId) {
            throw disabled();
        }
        @Override public FileMetadata metadata(String fileId) {
            throw disabled();
        }
        @Override public List<FileMetadata> listByOwner(String ownerModule, String ownerRefType, Long ownerRefId) {
            throw disabled();
        }
        private static FileException disabled() {
            return new FileException(FileException.Code.NO_ACTIVE_BACKEND,
                    "file storage 已禁用：wen-saas-boot.file.enabled=false");
        }
    }

    static final class NoopFileCenterService implements FileCenterService {
        @Override public FolderView createFolder(String space, Long parentId, String name) { throw disabled(); }
        @Override public FolderView renameFolder(Long folderId, String newName) { throw disabled(); }
        @Override public void moveFolder(Long folderId, Long newParentId) { throw disabled(); }
        @Override public void deleteFolder(Long folderId) { throw disabled(); }
        @Override public FileCenterListView list(String space, Long folderId, String keyword, int page, int size) { throw disabled(); }
        @Override public FileUploadResult upload(String space, Long folderId, String fileName, String mimeType, long sizeBytes, InputStream content) { throw disabled(); }
        @Override public void renameFile(String fileId, String newName) { throw disabled(); }
        @Override public void moveFile(String fileId, String targetSpace, Long targetFolderId) { throw disabled(); }
        @Override public void deleteFile(String fileId) { throw disabled(); }
        @Override public RecycleListView listRecycle(String space, int page, int size) { throw disabled(); }
        @Override public void restore(List<String> fileIds, List<Long> folderIds) { throw disabled(); }
        @Override public void purge(List<String> fileIds, List<Long> folderIds) { throw disabled(); }
        @Override public QuotaView quota() { throw disabled(); }
        private static FileCenterException disabled() {
            return new FileCenterException(FileCenterException.Code.INVALID_ARGUMENT,
                    "file storage 已禁用：wen-saas-boot.file.enabled=false");
        }
    }

    static final class NoopFileShareService implements FileShareService {
        @Override public FileShareInfo createShare(FileShareCreateRequest request) { throw disabled(); }
        @Override public FileShareInfo getShare(String shareToken) { throw disabled(); }
        @Override public FileShareConsumeResult consumeShare(String shareToken, String passwordPlain) { throw disabled(); }
        @Override public List<FileShareInfo> listShares(String fileId) { throw disabled(); }
        @Override public void revokeShare(String shareToken) { throw disabled(); }
        @Override public FileShareInfo getPublicShare(String shareToken) { throw disabled(); }
        @Override public FileShareConsumeResult consumePublicShare(String shareToken, String passwordPlain) { throw disabled(); }
        private static FileException disabled() {
            return new FileException(FileException.Code.NO_ACTIVE_BACKEND,
                    "file storage 已禁用：wen-saas-boot.file.enabled=false");
        }
    }
}
