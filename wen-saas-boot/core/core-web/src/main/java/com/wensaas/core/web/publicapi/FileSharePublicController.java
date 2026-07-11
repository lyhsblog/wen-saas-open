package com.wensaas.core.web.publicapi;

import com.wensaas.core.api.auth.permission.PublicEndpoint;
import com.wensaas.core.api.file.share.FileShareConsumeResult;
import com.wensaas.core.api.file.share.FileShareService;
import com.wensaas.core.api.tenant.CrossTenantQuery;
import com.wensaas.core.web.file.dto.ConsumeShareRequestDto;
import com.wensaas.core.web.publicapi.dto.PublicShareView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件免登录分享落地页（FC-901）公开入口。
 *
 * <p>对应内部鉴权版 {@code FileShareController}：此处面向租户外匿名访客，
 * token 即权限（链接级：密码 + 次数 + 过期）。
 *
 * <ul>
 *   <li>{@link PublicEndpoint}：{@code AuthenticationFilter} 不强制登录态</li>
 *   <li>{@link CrossTenantQuery}：整请求绕过 MP TenantLine（无 {@code TenantContext}，
 *       token 自携租户归属，文件按全局唯一 fileId 取）</li>
 *   <li>错误码经全局 {@code FileRestExceptionAdvice} 统一映射：
 *       404 不存在 / 401 需密码 / 403 密码错 / 410 过期或撤销 / 429 超限</li>
 * </ul>
 */
@Tag(name = "public-file-share", description = "免登录文件分享落地页")
@SecurityRequirements
@PublicEndpoint
@CrossTenantQuery
@RestController
@RequestMapping("/api/v1/public/shares")
public class FileSharePublicController {

    private final FileShareService shareService;

    public FileSharePublicController(FileShareService shareService) {
        this.shareService = shareService;
    }

    @Operation(summary = "查看分享（免登录）",
            description = "落地页渲染用；不消耗次数。需密码的分享 hasPassword=true 但不返回 URL")
    @GetMapping("/{shareToken}")
    public PublicShareView get(@PathVariable String shareToken) {
        return PublicShareView.from(shareService.getPublicShare(shareToken));
    }

    @Operation(summary = "消费分享并获取下载 URL（免登录）",
            description = "密码校验 + 原子计数自增 + 返回签名下载 URL")
    @PostMapping("/{shareToken}/download")
    public FileShareConsumeResult download(@PathVariable String shareToken,
                                           @RequestBody(required = false) ConsumeShareRequestDto dto) {
        String password = dto == null ? null : dto.password();
        return shareService.consumePublicShare(shareToken, password);
    }
}
