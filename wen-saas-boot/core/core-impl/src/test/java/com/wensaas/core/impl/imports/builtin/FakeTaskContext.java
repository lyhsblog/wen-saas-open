package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.task.TaskContext;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 最小 TaskContext 桩（单测用），捕获计数与结果。 */
final class FakeTaskContext implements TaskContext {

    private final Map<String, Object> params;
    final AtomicInteger success = new AtomicInteger();
    final AtomicInteger failed = new AtomicInteger();
    final AtomicInteger total = new AtomicInteger(-1);
    final AtomicReference<Object> result = new AtomicReference<>();

    FakeTaskContext(Map<String, Object> params) {
        this.params = params;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> result() {
        return (Map<String, Object>) result.get();
    }

    @Override public Long taskId() { return 1L; }
    @Override public Long tenantId() { return 9L; }
    @Override public Map<String, Object> params() { return params; }
    @Override public void setTotal(int total) { this.total.set(total); }
    @Override public void updateProgress(int percent, int processed) { }
    @Override public void recordSuccess(int delta) { success.addAndGet(delta); }
    @Override public void recordFailure(int delta) { failed.addAndGet(delta); }
    @Override public boolean isCancelled() { return false; }
    @Override public void setResult(Object resultData) { result.set(resultData); }
}
