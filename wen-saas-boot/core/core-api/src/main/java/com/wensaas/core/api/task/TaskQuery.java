package com.wensaas.core.api.task;

/**
 * 任务中心列表查询条件（任务中心只读 API）。
 *
 * @param status 状态过滤；null 表示不限
 * @param type   类型过滤；null 表示不限
 * @param offset 分页偏移（0-based）
 * @param limit  分页大小
 */
public record TaskQuery(TaskStatus status, TaskType type, int offset, int limit) {
}
