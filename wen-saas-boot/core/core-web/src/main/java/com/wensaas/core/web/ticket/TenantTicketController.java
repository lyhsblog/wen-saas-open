package com.wensaas.core.web.ticket;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ticket.TicketAttachmentView;
import com.wensaas.core.api.ticket.TicketDetailView;
import com.wensaas.core.api.ticket.TicketMessageView;
import com.wensaas.core.api.ticket.TicketPage;
import com.wensaas.core.api.ticket.TicketPriority;
import com.wensaas.core.api.ticket.TicketService;
import com.wensaas.core.api.ticket.TicketStatus;
import com.wensaas.core.api.ticket.TicketType;
import com.wensaas.core.web.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 租户后台 · 我的工单。
 *
 * <p>提交 / 查看自己的工单对所有租户成员开放；查看本租户全部工单（{@code /all}）需
 * {@code base.ticket.view_all}（默认无）。
 */
@Tag(name = "tenant-ticket", description = "租户后台 · 工单")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/base/tickets")
public class TenantTicketController {

    private final TicketService service;

    public TenantTicketController(TicketService service) {
        this.service = service;
    }

    @Operation(summary = "提交工单")
    @Auditable(module = "ticket", action = "提交工单", objectType = "",
            objectIdExpr = "#result.summary().id()", objectDescExpr = "#result.summary().ticketNo()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetailView submit(@Valid @RequestBody SubmitRequest req) {
        return service.submit(new TicketService.SubmitCommand(
                req.title(), req.type(), req.priority(), req.content()));
    }

    @Operation(summary = "我提交的工单（可按状态过滤）")
    @GetMapping
    public TicketPage myTickets(@RequestParam(required = false) TicketStatus status,
                                @Valid @ParameterObject PageQuery pageQuery) {
        return service.myTickets(status, pageQuery.page(), pageQuery.size());
    }

    @Operation(summary = "本租户全部工单（需 base.ticket.view_all）")
    @RequirePermission("base.ticket.view_all")
    @GetMapping("/all")
    public TicketPage tenantTickets(@RequestParam(required = false) TicketStatus status,
                                    @RequestParam(required = false) String keyword,
                                    @Valid @ParameterObject PageQuery pageQuery) {
        return service.tenantTickets(status, keyword, pageQuery.page(), pageQuery.size());
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public TicketDetailView detail(@PathVariable long id) {
        return service.detail(id);
    }

    @Operation(summary = "追加回复（已解决/已关闭 7 天内追加视为重启）")
    @Auditable(module = "ticket", action = "回复", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/reply")
    public TicketMessageView reply(@PathVariable long id, @Valid @RequestBody ContentRequest req) {
        return service.reply(id, req.content());
    }

    @Operation(summary = "确认问题已解决，关闭工单")
    @Auditable(module = "ticket", action = "确认解决", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<Void> confirmResolved(@PathVariable long id) {
        service.confirmResolved(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "重新打开工单（已解决 / 已关闭 7 天内）")
    @Auditable(module = "ticket", action = "重新打开", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/reopen")
    public ResponseEntity<Void> reopen(@PathVariable long id) {
        service.reopen(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "上传附件（截图，单附件≤10MB，单工单累计≤50MB）")
    @Auditable(module = "ticket", action = "添加附件", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/attachments")
    public TicketAttachmentView addAttachment(@PathVariable long id,
                                              @RequestParam(required = false) Long messageId,
                                              @RequestParam("file") MultipartFile file) throws IOException {
        return service.addAttachment(id, messageId, file.getOriginalFilename(),
                file.getContentType(), file.getSize(), file.getInputStream());
    }

    @Operation(summary = "附件下载签名 URL")
    @GetMapping("/{id}/attachments/{attachmentId}/url")
    public DownloadUrl attachmentUrl(@PathVariable long id, @PathVariable long attachmentId) {
        return new DownloadUrl(service.attachmentDownloadUrl(id, attachmentId));
    }

    // ===================== 请求 / 响应 DTO =====================

    public record SubmitRequest(
            @NotBlank String title,
            @NotNull TicketType type,
            @NotNull TicketPriority priority,
            @NotBlank String content) {
    }

    public record ContentRequest(@NotBlank String content) {}

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class DownloadUrl {
        private String url;
    }
}
