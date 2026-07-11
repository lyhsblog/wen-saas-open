package com.wensaas.core.web.task;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.task.TaskPage;
import com.wensaas.core.api.task.TaskQuery;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.task.TaskSnapshot;
import com.wensaas.core.api.task.TaskStatus;
import com.wensaas.core.api.task.TaskType;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.web.common.PageQuery;
import com.wensaas.core.web.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * 任务中心只读 API（个人「我的任务」）。
 *
 * <p>承接导入 / 导出 / 批量等所有异步任务；仅认证即可访问，数据范围限当前用户（creator=self）+ 当前租户。
 * 导入框架的执行进度 / 结果下载统一复用本控制器的 {@code /stream} 与 {@code /download}。
 */
@Tag(name = "tasks", description = "任务中心 · 我的任务")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskStreamService streamService;
    private final FileService fileService;

    public TaskController(TaskService taskService, TaskStreamService streamService, FileService fileService) {
        this.taskService = taskService;
        this.streamService = streamService;
        this.fileService = fileService;
    }

    @Operation(summary = "我的任务列表",
            description = "按 created_at 倒序分页；可按 status / type 过滤（小写枚举名，如 running / import）")
    @GetMapping
    public PageResponse<TaskView> list(
            @Parameter(description = "状态过滤：pending/running/completed/failed/cancelled ...")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "类型过滤：import/export/batch/other")
            @RequestParam(value = "type", required = false) String type,
            @Valid @ParameterObject PageQuery pageQuery) {
        Long me = UserContext.require().userId();
        TaskQuery query = new TaskQuery(
                parseEnum(TaskStatus.class, status, "status"),
                parseEnum(TaskType.class, type, "type"),
                pageQuery.offset(), pageQuery.size());
        TaskPage result = taskService.queryMine(me, query);
        return new PageResponse<>(result.records().stream().map(TaskView::from).toList(),
                result.total(), pageQuery.page(), pageQuery.size());
    }

    @Operation(summary = "任务详情", description = "进度 / 计数等运行时字段优先读 Redis、DB 兜底；跨租户 404")
    @GetMapping("/{id}")
    public TaskView get(@PathVariable Long id) {
        return TaskView.from(taskService.get(id));
    }

    @Operation(summary = "取消任务", description = "仅 RUNNING 可取消；置 CANCELLING + Redis cancel 标志，handler 周期检查后优雅退出")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        taskService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "进度 SSE 流",
            description = "每 500ms 推一帧任务快照；终态推最后一帧后关闭。打开前同步校验存在性，不存在直接 404")
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        long tenantId = TenantContext.require();
        taskService.get(id); // 同步校验存在性 + 租户归属（不存在抛 TASK_NOT_FOUND → 404）
        return streamService.open(id, tenantId);
    }

    @Operation(summary = "下载任务结果",
            description = "重定向到结果文件签名 URL（5 分钟有效）；无结果文件 404。导入失败明细 / 导出文件统一走此口")
    @GetMapping("/{id}/download")
    public ResponseEntity<Void> download(@PathVariable Long id) {
        TaskSnapshot snap = taskService.get(id);
        Map<String, Object> result = snap.resultData();
        Object fileId = result == null ? null : result.get("fileId");
        if (fileId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务暂无可下载结果");
        }
        String url = fileService.signedDownloadUrl(fileId.toString(), Duration.ofMinutes(5));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "未知的 " + field + ": " + raw);
        }
    }
}
