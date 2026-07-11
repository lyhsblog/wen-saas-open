package com.wensaas.core.impl.task.execution;

import com.alibaba.fastjson2.JSON;
import com.wensaas.core.api.task.TaskContext;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.wensaas.core.impl.task.progress.TaskProgressStore;

/**
 * {@link TaskContext} 内部实现。线程模型：单任务在单消费线程顺序执行，非线程安全；
 * 计数器用 atomic 类型仅为防止 handler 内部启用了 {@code Async.runWithContext} 子任务。
 *
 * <p>updateProgress 节流：相邻两次写 Redis 间隔不少于 {@code throttleMs}（终态写入由 TaskRunner 强制）。
 */
public class TaskContextImpl implements TaskContext {

    private final long taskId;
    private final long tenantId;
    private final Map<String, Object> params;
    private final TaskProgressStore progressStore;
    private final long throttleMs;

    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger success = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicLong lastWriteAt = new AtomicLong(0L);
    private volatile Integer total;
    private volatile Object resultData;

    public TaskContextImpl(long taskId, long tenantId, Map<String, Object> params,
                           TaskProgressStore progressStore, long throttleMs) {
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.params = params == null ? Map.of() : params;
        this.progressStore = progressStore;
        this.throttleMs = throttleMs;
    }

    @Override
    public Long taskId() {
        return taskId;
    }

    @Override
    public Long tenantId() {
        return tenantId;
    }

    @Override
    public Map<String, Object> params() {
        return params;
    }

    @Override
    public void updateProgress(int percent, int processedDelta) {
        progress.set(Math.max(0, Math.min(100, percent)));
        processed.set(processedDelta);
        long now = System.currentTimeMillis();
        long prev = lastWriteAt.get();
        if (now - prev >= throttleMs && lastWriteAt.compareAndSet(prev, now)) {
            progressStore.writeProgress(taskId, snapshot());
        }
    }

    @Override
    public void recordSuccess(int delta) {
        success.addAndGet(delta);
    }

    @Override
    public void recordFailure(int delta) {
        failed.addAndGet(delta);
    }

    @Override
    public boolean isCancelled() {
        return progressStore.isCancelled(taskId);
    }

    @Override
    public void setResult(Object resultData) {
        this.resultData = resultData;
    }

    /** 当前进度快照。 */
    TaskProgressStore.TaskProgress snapshot() {
        return new TaskProgressStore.TaskProgress(
                progress.get(), total, processed.get(), success.get(), failed.get(),
                null, System.currentTimeMillis());
    }

    /** TaskRunner 终态读取计数器。 */
    int progressValue() { return progress.get(); }
    int processedValue() { return processed.get(); }
    int successValue() { return success.get(); }
    int failedValue() { return failed.get(); }
    Integer totalValue() { return total; }

    @Override
    public void setTotal(int total) { this.total = total; }

    /** TaskRunner 终态读取业务结果。返回 Map（JacksonTypeHandler 友好）；非 Map 包成 {value: ...}。 */
    @SuppressWarnings("unchecked")
    Map<String, Object> resultValueAsMap() {
        if (resultData == null) {
            return null;
        }
        if (resultData instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        // 任意 POJO → JSON → Map
        String json = JSON.toJSONString(resultData);
        return JSON.parseObject(json, Map.class);
    }
}
