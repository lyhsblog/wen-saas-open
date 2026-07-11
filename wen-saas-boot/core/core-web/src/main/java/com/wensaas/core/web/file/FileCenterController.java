package com.wensaas.core.web.file;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.file.FileUploadResult;
import com.wensaas.core.api.file.center.FileCenterListView;
import com.wensaas.core.api.file.center.FileCenterService;
import com.wensaas.core.api.file.center.FolderView;
import com.wensaas.core.api.file.center.QuotaView;
import com.wensaas.core.api.file.center.RecycleListView;
import com.wensaas.core.web.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 租户后台 · 文件中心。
 *
 * <p>三空间（mine / shared）+ 文件夹树 + 回收站 + 配额。下载 / 预览签名 URL 复用
 * {@code FileController GET /api/v1/files/{id}/url}；链接分享复用 {@code FileShareController}。
 *
 * <p>鉴权：默认 {@code AuthenticationFilter}（需登录）；空间隔离由 service 按 tenant + 当前用户处理。
 */
@Tag(name = "file-center", description = "租户后台 · 文件中心")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/file-center")
public class FileCenterController {

    private final FileCenterService service;

    public FileCenterController(FileCenterService service) {
        this.service = service;
    }

    // ---------- 列表 ----------

    @Operation(summary = "列目录（子文件夹 + 文件分页 + 面包屑）")
    @GetMapping("/folders")
    public FileCenterListView list(@RequestParam String space,
                                   @RequestParam(required = false) Long folderId,
                                   @RequestParam(required = false) String keyword,
                                   @Valid @ParameterObject PageQuery pageQuery) {
        return service.list(space, folderId, keyword, pageQuery.page(), pageQuery.size());
    }

    // ---------- 文件夹 ----------

    @Operation(summary = "新建文件夹")
    @PostMapping("/folders")
    public FolderView createFolder(@Valid @RequestBody CreateFolderRequest req) {
        return service.createFolder(req.space(), req.parentId(), req.name());
    }

    @Operation(summary = "重命名文件夹")
    @Auditable(module = "__core__", action = "重命名文件夹", objectType = "", objectIdExpr = "#folderId")
    @PutMapping("/folders/{folderId}/rename")
    public FolderView renameFolder(@PathVariable Long folderId, @Valid @RequestBody NameRequest req) {
        return service.renameFolder(folderId, req.name());
    }

    @Operation(summary = "移动文件夹")
    @PutMapping("/folders/{folderId}/move")
    public ResponseEntity<Void> moveFolder(@PathVariable Long folderId, @RequestBody MoveFolderRequest req) {
        service.moveFolder(folderId, req.newParentId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "删除文件夹（级联进回收站）")
    @Auditable(module = "__core__", action = "删除文件夹", objectType = "", objectIdExpr = "#folderId")
    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<Void> deleteFolder(@PathVariable Long folderId) {
        service.deleteFolder(folderId);
        return ResponseEntity.noContent().build();
    }

    // ---------- 文件 ----------

    @Operation(summary = "上传文件（multipart，≤100MB；重名自动后缀；超配额拒绝）")
    @PostMapping("/files")
    public FileUploadResult upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam String space,
                                   @RequestParam(required = false) Long folderId) throws IOException {
        return service.upload(space, folderId, file.getOriginalFilename(), file.getContentType(),
                file.getSize(), file.getInputStream());
    }

    @Operation(summary = "重命名文件")
    @Auditable(module = "__core__", action = "重命名文件", objectType = "", objectIdExpr = "#fileId")
    @PutMapping("/files/{fileId}/rename")
    public ResponseEntity<Void> renameFile(@PathVariable String fileId, @Valid @RequestBody NameRequest req) {
        service.renameFile(fileId, req.name());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "移动文件（重名自动后缀）")
    @PutMapping("/files/{fileId}/move")
    public ResponseEntity<Void> moveFile(@PathVariable String fileId, @Valid @RequestBody MoveFileRequest req) {
        service.moveFile(fileId, req.targetSpace(), req.targetFolderId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "删除文件（进回收站）")
    @Auditable(module = "__core__", action = "删除文件", objectType = "", objectIdExpr = "#fileId")
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId) {
        service.deleteFile(fileId);
        return ResponseEntity.noContent().build();
    }

    // ---------- 回收站 ----------

    @Operation(summary = "回收站列表")
    @GetMapping("/recycle")
    public RecycleListView recycle(@RequestParam String space,
                                   @Valid @ParameterObject PageQuery pageQuery) {
        return service.listRecycle(space, pageQuery.page(), pageQuery.size());
    }

    @Operation(summary = "还原（文件 / 文件夹）")
    @PostMapping("/recycle/restore")
    public ResponseEntity<Void> restore(@RequestBody RecycleCommand req) {
        service.restore(req.fileIds(), req.folderIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "彻底删除（文件 / 文件夹）")
    @Auditable(module = "__core__", action = "彻底删除", objectType = "")
    @DeleteMapping("/recycle")
    public ResponseEntity<Void> purge(@RequestBody RecycleCommand req) {
        service.purge(req.fileIds(), req.folderIds());
        return ResponseEntity.noContent().build();
    }

    // ---------- 配额 ----------

    @Operation(summary = "容量配额（已用 / 总量）")
    @GetMapping("/quota")
    public QuotaView quota() {
        return service.quota();
    }

    // ===================== 请求 DTO =====================

    public record CreateFolderRequest(@NotBlank String space, Long parentId, @NotBlank String name) {}

    public record NameRequest(@NotBlank String name) {}

    public record MoveFolderRequest(Long newParentId) {}

    public record MoveFileRequest(@NotBlank String targetSpace, Long targetFolderId) {}

    public record RecycleCommand(List<String> fileIds, List<Long> folderIds) {}
}
