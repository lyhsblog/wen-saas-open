package com.wensaas.core.web.notification;

import com.wensaas.core.api.notification.inbox.DeliveryDetail;
import com.wensaas.core.api.notification.inbox.InboxPage;
import com.wensaas.core.api.notification.inbox.MessageDetailView;
import com.wensaas.core.api.notification.inbox.MessageInboxService;
import com.wensaas.core.api.notification.inbox.UnreadSummary;
import com.wensaas.core.web.common.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户后台 · 消息中心 / 站内信收件箱。
 *
 * <p>无需 {@code @RequirePermission}：所有成员都有自己的收件箱，service 按 {@code recipient_id=当前用户}
 * 行级隔离。关联对象「内容不可见」降级在详情序列化阶段自动完成。
 */
@Tag(name = "message-inbox", description = "租户后台 · 消息中心收件箱")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/messages/inbox")
public class MessageInboxController {

    private final MessageInboxService service;

    public MessageInboxController(MessageInboxService service) {
        this.service = service;
    }

    @Operation(summary = "收件箱列表（分类 / 已读态 / 关键字 / 归档 + 分页）")
    @GetMapping
    public InboxPage list(@RequestParam(required = false) String category,
                          @RequestParam(defaultValue = "ALL") String readState,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "false") boolean archived,
                          @Valid @ParameterObject PageQuery pageQuery) {
        return service.list(category, readState, keyword, archived, pageQuery.page(), pageQuery.size());
    }

    @Operation(summary = "消息详情（打开即标已读；关联对象无权时正文降级）")
    @GetMapping("/{id}")
    public MessageDetailView detail(@PathVariable Long id) {
        return service.open(id);
    }

    @Operation(summary = "单条标已读")
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        service.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "全部 / 按分类标已读")
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@RequestParam(required = false) String category) {
        service.markAllRead(category);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "删除消息")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "未读汇总（铃铛：总数 + 分类）")
    @GetMapping("/unread-count")
    public UnreadSummary unreadCount() {
        return service.unreadSummary();
    }

    @Operation(summary = "投递明细（各通道结果）")
    @GetMapping("/{id}/deliveries")
    public List<DeliveryDetail> deliveries(@PathVariable Long id) {
        return service.deliveries(id);
    }
}
