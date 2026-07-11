package com.wensaas.core.api.task;

import java.util.Map;
import java.util.Objects;

/**
 * 任务提交请求。
 *
 * <p>任务中心不做通用审批：提交即落 PENDING 进入执行链路。导出类操作若需审批，
 * 由数据合规模块（sys_export_request 审批单）在提交任务之前把关，通过后再走本入口。
 *
 * @param type        任务类型
 * @param name        显示名（可空，建议人类可读：导入成员-客户名单.xlsx）
 * @param handlerCode 处理器标识（必须在 TaskHandlerRegistry 已注册）
 * @param params      业务参数（JSONB 落 sys_task.task_params）；可空
 */
public record SubmitRequest(
        TaskType type,
        String name,
        String handlerCode,
        Map<String, Object> params) {

    public SubmitRequest {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handlerCode, "handlerCode");
        if (handlerCode.isBlank()) {
            throw new IllegalArgumentException("handlerCode 不可为空");
        }
    }

    public static SubmitRequest of(TaskType type, String handlerCode) {
        return new SubmitRequest(type, null, handlerCode, Map.of());
    }
}
