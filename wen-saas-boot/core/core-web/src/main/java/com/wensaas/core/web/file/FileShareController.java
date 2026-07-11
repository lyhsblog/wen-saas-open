package com.wensaas.core.web.file;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.file.share.FileShareConsumeResult;
import com.wensaas.core.api.file.share.FileShareInfo;
import com.wensaas.core.api.file.share.FileShareService;
import com.wensaas.core.web.file.dto.ConsumeShareRequestDto;
import com.wensaas.core.web.file.dto.CreateShareRequestDto;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文件链接分享 REST 入口。
 *
 * <p>鉴权：所有端点都走默认 {@code AuthenticationFilter}（P0 仅限租户内成员，需登录态）。
 * 公开分享（免登录）推 v3 用独立 {@code /api/v1/public/shares/**} 路径。
 */
@Tag(name = "file-shares", description = "文件链接分享")
@RestController
@RequestMapping("/api/v1/files")
public class FileShareController {

    private final FileShareService shareService;

    public FileShareController(FileShareService shareService) {
        this.shareService = shareService;
    }

    @Operation(summary = "创建分享", description = "可选 7d/30d/永久 + 4-6 位密码 + 最大下载次数")
    @Auditable(module = "__core__", action = "创建分享", objectType = "",
            objectIdExpr = "#fileId", objectDescExpr = "#result.fileName()")
    @PostMapping("/{fileId}/shares")
    public FileShareInfo create(@PathVariable String fileId,
                                @Valid @RequestBody CreateShareRequestDto dto) {
        return shareService.createShare(dto.toRequest(fileId));
    }

    @Operation(summary = "列出当前文件的活跃分享")
    @GetMapping("/{fileId}/shares")
    public List<FileShareInfo> list(@PathVariable String fileId) {
        return shareService.listShares(fileId);
    }

    @Operation(summary = "查看分享", description = "不消耗次数。需密码的分享：hasPassword=true 但不返回 URL")
    @GetMapping("/shares/{shareToken}")
    public FileShareInfo get(@PathVariable String shareToken) {
        return shareService.getShare(shareToken);
    }

    @Operation(summary = "消费分享并获取下载 URL",
            description = "密码校验 + 原子计数自增 + 返回签名下载 URL。"
                    + "失败码：401 需密码 / 403 密码错 / 410 过期或撤销 / 429 超限 / 404 不存在")
    @PostMapping("/shares/{shareToken}/download")
    public FileShareConsumeResult consume(@PathVariable String shareToken,
                                          @RequestBody(required = false) ConsumeShareRequestDto dto) {
        String password = dto == null ? null : dto.password();
        return shareService.consumeShare(shareToken, password);
    }

    @Operation(summary = "撤销分享", description = "status=revoked + 软删；同一文件可重新建分享")
    @Auditable(module = "__core__", action = "撤销分享", objectType = "", objectIdExpr = "#shareToken")
    @DeleteMapping("/shares/{shareToken}")
    public ResponseEntity<Void> revoke(@PathVariable String shareToken) {
        shareService.revokeShare(shareToken);
        return ResponseEntity.noContent().build();
    }
}
