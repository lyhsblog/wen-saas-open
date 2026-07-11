package com.wensaas.core.api.task;

/**
 * 任务类型：导入 / 导出 / 批量操作 / 其他，驱动任务中心列表的类型筛选。
 */
public enum TaskType {
    IMPORT,
    EXPORT,
    BATCH,
    OTHER
}
