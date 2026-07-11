package com.wensaas.core.web.task;

import com.wensaas.core.api.task.TaskSnapshot;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 任务中心对外视图。
 *
 * <p>{@code type} / {@code status} 输出小写枚举名（如 {@code import} / {@code running}），
 * 与任务进度 SSE 推送的契约一致；{@code id} / {@code creatorId} 经 JsonConfig 序列化为字符串。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class TaskView {
    private Long id;
    private String type;
    private String name;
    private String handlerCode;
    private String status;
    private int progress;
    private Integer totalCount;
    private Integer processedCount;
    private Integer successCount;
    private Integer failedCount;
    private Map<String, Object> resultData;
    private String errorMessage;
    private Long creatorId;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime expiresAt;

    public static TaskView from(TaskSnapshot s) {
        return new TaskView(
                s.id(),
                s.type().name().toLowerCase(Locale.ROOT),
                s.name(),
                s.handlerCode(),
                s.status().name().toLowerCase(Locale.ROOT),
                s.progress(),
                s.totalCount(),
                s.processedCount(),
                s.successCount(),
                s.failedCount(),
                s.resultData(),
                s.errorMessage(),
                s.creatorId(),
                s.createdAt(),
                s.startedAt(),
                s.finishedAt(),
                s.expiresAt());
    }
}
