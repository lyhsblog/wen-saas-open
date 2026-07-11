package com.wensaas.core.api.task;

import java.util.Map;

/**
 * TaskHandler 执行上下文。提供任务标识、参数、进度上报、取消检测、结果回传。
 *
 * <p>线程模型：单个任务在单个消费线程内顺序执行，TaskContext 实例非线程安全；如 handler 内部
 * 启用多线程，请通过 {@code com.wensaas.core.impl.async.Async} 工具传递上下文。
 */
public interface TaskContext {

    /** 当前任务 ID（雪花）。 */
    Long taskId();

    /** 提交方租户 ID。 */
    Long tenantId();

    /** 提交时透传的业务参数（来自 {@link SubmitRequest#params()}）。 */
    Map<String, Object> params();

    /**
     * 声明本次任务的总条数（用于进度分母 / {@code sys_task.total_count} 回写）。
     * 在进入处理循环前调用一次；不调用时 total 为 null，前端进度退化为「已处理 X · Z%」无分母。
     */
    void setTotal(int total);

    /**
     * 上报进度。
     *
     * @param percent 0..100
     * @param processed 已处理条数
     */
    void updateProgress(int percent, int processed);

    /** 累加成功条数（内存计数 + 周期化 flush 到 Redis）。 */
    void recordSuccess(int delta);

    /** 累加失败条数。 */
    void recordFailure(int delta);

    /**
     * 是否已被用户取消。长循环类任务应在循环体内周期性检查（建议每 N 条调一次）。
     * 检测到 true 时应优雅退出（必要时回滚已处理部分）。
     */
    boolean isCancelled();

    /**
     * 设置任务结果（任意可序列化对象，落到 {@code sys_task.result_data} JSONB）。
     * 建议在 handler.execute 收尾处调用一次。
     */
    void setResult(Object resultData);
}
