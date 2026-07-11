package com.wensaas.core.web.ops;

import com.wensaas.core.api.audit.Auditable;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.api.ticket.OpsTicketService;
import com.wensaas.core.api.ticket.OpsTicketService.AssigneeOption;
import com.wensaas.core.api.ticket.OpsTicketService.OpsTicketQuery;
import com.wensaas.core.api.ticket.TicketAttachmentView;
import com.wensaas.core.api.ticket.TicketDetailView;
import com.wensaas.core.api.ticket.TicketMessageView;
import com.wensaas.core.api.ticket.TicketPage;
import com.wensaas.core.api.ticket.TicketPriority;
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
import org.springframework.http.ResponseEntity;
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
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 运营后台 · 工单管理。运营客服跨租户处理。
 *
 * <p>只读端点要 {@code ops.ticket.view}；处理类要 {@code ops.ticket.handle}；
 * 强制重启已关闭工单要 {@code ops.ticket.reopen}（仅超管）。列表/操作按 dataScope 收敛。
 */
@Tag(name = "ops-ticket", description = "运营后台 · 工单管理")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/ops/tickets")
public class OpsTicketController {

    private final OpsTicketService service;

    public OpsTicketController(OpsTicketService service) {
        this.service = service;
    }

    @Operation(summary = "工单管理列表（多条件 + SLA 状态 + 我的工单）")
    @RequirePermission("ops.ticket.view")
    @GetMapping
    public TicketPage list(@RequestParam(required = false) TicketStatus status,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false) Long tenantId,
                           @RequestParam(required = false) TicketType type,
                           @RequestParam(required = false) TicketPriority priority,
                           @RequestParam(required = false) Long assigneeId,
                           @RequestParam(required = false) String slaState,
                           @RequestParam(defaultValue = "false") boolean mineOnly,
                           @Valid @ParameterObject PageQuery pageQuery) {
        return service.list(new OpsTicketQuery(status, keyword, tenantId, type, priority,
                assigneeId, slaState, mineOnly, pageQuery.page(), pageQuery.size()));
    }

    @Operation(summary = "可分配客服列表（转单候选）")
    @RequirePermission("ops.ticket.view")
    @GetMapping("/assignees")
    public List<AssigneeOption> assignees() {
        return service.assigneeOptions();
    }

    @Operation(summary = "工单详情")
    @RequirePermission("ops.ticket.view")
    @GetMapping("/{id}")
    public TicketDetailView detail(@PathVariable long id) {
        return service.detail(id);
    }

    @Operation(summary = "认领：待处理 → 处理中")
    @RequirePermission("ops.ticket.handle")
    @Auditable(module = "ticket", action = "认领", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/claim")
    public ResponseEntity<Void> claim(@PathVariable long id) {
        service.claim(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "转单给其他客服")
    @RequirePermission("ops.ticket.handle")
    @Auditable(module = "ticket", action = "转交", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/transfer")
    public ResponseEntity<Void> transfer(@PathVariable long id, @Valid @RequestBody TransferRequest req) {
        service.transfer(id, req.assigneeId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "调整优先级（重算 SLA 截止）")
    @RequirePermission("ops.ticket.handle")
    @Auditable(module = "ticket", action = "调整优先级", objectType = "", objectIdExpr = "#id")
    @PutMapping("/{id}/priority")
    public ResponseEntity<Void> changePriority(@PathVariable long id, @Valid @RequestBody PriorityRequest req) {
        service.changePriority(id, req.priority());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "客服回复（待处理则自动认领转处理中）")
    @RequirePermission("ops.ticket.handle")
    @Auditable(module = "ticket", action = "回复", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/reply")
    public TicketMessageView reply(@PathVariable long id, @Valid @RequestBody ContentRequest req) {
        return service.reply(id, req.content());
    }

    @Operation(summary = "标记已解决：处理中 → 已解决")
    @RequirePermission("ops.ticket.handle")
    @Auditable(module = "ticket", action = "标记解决", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable long id) {
        service.resolve(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "关闭工单（填写关闭原因）")
    @RequirePermission("ops.ticket.handle")
    @Auditable(module = "ticket", action = "关闭工单", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@PathVariable long id, @Valid @RequestBody CloseRequest req) {
        service.close(id, req.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "强制重启已关闭工单（仅超管）")
    @RequirePermission("ops.ticket.reopen")
    @Auditable(module = "ticket", action = "强制重开", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/reopen")
    public ResponseEntity<Void> reopen(@PathVariable long id) {
        service.reopen(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "上传附件（单附件≤10MB，单工单累计≤50MB）")
    @RequirePermission("ops.ticket.handle")
    @Auditable(module = "ticket", action = "添加附件", objectType = "", objectIdExpr = "#id")
    @PostMapping("/{id}/attachments")
    public TicketAttachmentView addAttachment(@PathVariable long id,
                                              @RequestParam(required = false) Long messageId,
                                              @RequestParam("file") MultipartFile file) throws IOException {
        return service.addAttachment(id, messageId, file.getOriginalFilename(),
                file.getContentType(), file.getSize(), file.getInputStream());
    }

    @Operation(summary = "附件下载签名 URL")
    @RequirePermission("ops.ticket.view")
    @GetMapping("/{id}/attachments/{attachmentId}/url")
    public DownloadUrl attachmentUrl(@PathVariable long id, @PathVariable long attachmentId) {
        return new DownloadUrl(service.attachmentDownloadUrl(id, attachmentId));
    }

    // ===================== 请求 / 响应 DTO =====================

    public record TransferRequest(@NotNull Long assigneeId) {}

    public record PriorityRequest(@NotNull TicketPriority priority) {}

    public record ContentRequest(@NotBlank String content) {}

    public record CloseRequest(@NotBlank String reason) {}

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
