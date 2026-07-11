package com.wensaas.core.web.file;

import com.wensaas.core.api.audit.AuditObjects;
import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.file.FileMetadata;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.file.FileUploadRequest;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.FileVisibility;
import com.wensaas.core.api.file.SignedUploadUrl;
import com.wensaas.core.web.file.dto.SignedUploadRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 文件存储业务高层 REST 入口。
 *
 * <p>鉴权：默认链路走 {@code AuthenticationFilter}（需登录）。Local 后端的真实上传/下载端点
 * 由 {@link LocalFileController} 提供（HMAC 自鉴权 @PublicEndpoint）。
 */
@Tag(name = "files", description = "文件存储业务高层 API")
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "申请上传签名 URL",
            description = "前端直传 OSS 的签名 URL；底座先写 sys_file state=uploading，需配合 confirm 端点完成上传")
    @PostMapping("/upload-url")
    public SignedUploadUrl requestUploadUrl(@Valid @RequestBody SignedUploadRequestDto req) {
        return fileService.requestSignedUpload(req.toRequest());
    }

    @Operation(summary = "确认上传完成",
            description = "客户端 PUT 完成后调此，底座 HEAD 校验后端真实存在，state → active")
    @PostMapping("/{fileId}/confirm")
    public FileUploadResult confirmUpload(@PathVariable String fileId) {
        return fileService.confirmUpload(fileId);
    }

    @Operation(summary = "签名下载 URL",
            description = "5 分钟过期签名 URL；Redis 缓存 4 分钟。business-only 文件经 FilePermissionChecker 鉴权")
    @GetMapping("/{fileId}/url")
    public Map<String, Object> signedDownloadUrl(@PathVariable String fileId) {
        String url = fileService.signedDownloadUrl(fileId, Duration.ofMinutes(5));
        return Map.of("url", url);
    }

    @Operation(summary = "文件元数据", description = "不含后端凭证；跨租户访问 404")
    @GetMapping("/{fileId}")
    public FileMetadata metadata(@PathVariable String fileId) {
        return fileService.metadata(fileId);
    }

    @Operation(summary = "软删文件",
            description = "@TableLogic 软删 + Redis URL 缓存 evict；定时任务 30 天后物理清理后端文件")
    @Auditable(module = "__core__", action = "删除文件", objectType = "", objectIdExpr = "#fileId")
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable String fileId) {
        // ② 审计对象名：删前取文件名登记（与 delete 同样的存在性校验）
        AuditObjects.add(null, fileId, fileService.metadata(fileId).fileName());
        fileService.delete(fileId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "按业务对象列出文件", description = "活跃记录，按 uploaded_at 倒序；过 MP TenantLine 自动按租户隔离")
    @GetMapping
    public List<FileMetadata> listByOwner(@RequestParam String ownerModule,
                                          @RequestParam String ownerRefType,
                                          @RequestParam Long ownerRefId) {
        return fileService.listByOwner(ownerModule, ownerRefType, ownerRefId);
    }

    @Operation(summary = "Multipart 直传", description = "适合 ≤ 100MB 简单场景；大文件请走 upload-url + confirm 二步")
    @PostMapping("/upload")
    public FileUploadResult uploadMultipart(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "visibility", defaultValue = "file-center")
                                            String visibilityCode,
                                            @RequestParam(value = "ownerModule", required = false)
                                            String ownerModule,
                                            @RequestParam(value = "ownerRefType", required = false)
                                            String ownerRefType,
                                            @RequestParam(value = "ownerRefId", required = false)
                                            Long ownerRefId,
                                            @RequestParam(value = "folderPath", required = false)
                                            String folderPath) throws IOException {
        FileVisibility visibility = FileVisibility.fromCode(visibilityCode);
        FileUploadRequest req = new FileUploadRequest(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream(),
                visibility,
                ownerModule, ownerRefType, ownerRefId, folderPath);
        return fileService.uploadDirect(req);
    }
}
