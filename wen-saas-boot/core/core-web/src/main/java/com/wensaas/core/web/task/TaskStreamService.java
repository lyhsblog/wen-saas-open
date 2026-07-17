package com.wensaas.core.web.task;

import com.wensaas.core.api.task.TaskException;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.task.TaskSnapshot;
import com.wensaas.core.api.tenant.TenantContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务进度 SSE 推送。
 *
 * <p>每 500ms 从 {@link TaskService#get} 读快照（内部 Redis 优先、DB 兜底）推一帧；
 * 终态推最后一帧后关闭。后台线程不持有请求 ThreadLocal，故每次读取用
 * {@link TenantContext#callAs} 重建租户上下文（MP TenantLine 据此隔离）。
 *
 * <p><b>并发模型</b>：单线程 {@code scheduler} 只负责「到点分发」，每帧真正的阻塞工作
 * （Redis+DB 读 + {@code emitter.send}）提交到<b>虚拟线程</b>执行器，故并发流数不再受固定线程池上限制约、
 * 慢 IO 不会相互阻塞累积推送延迟。每条流用「固定延迟自重排」（上一帧完成后再排下一帧）天然串行化相邻帧，
 * 无需 in-flight 守卫；流结束（终态 / 超时 / 客户端断开）后 {@code done} 置位，已排队的下一帧空转跳过。
 */
@Service
public class TaskStreamService {

    private static final Logger log = LoggerFactory.getLogger(TaskStreamService.class);
    private static final long POLL_MS = 500;
    private static final long MAX_DURATION_MS = Duration.ofMinutes(30).toMillis();

    private final TaskService taskService;

    /** 仅做定时分发，不跑阻塞工作，单线程足够。 */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "task-sse-scheduler");
        t.setDaemon(true);
        return t;
    });

    /** 每帧阻塞工作跑在虚拟线程：每流一线程、按需创建，无固定上限。 */
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

    public TaskStreamService(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 打开任务 {@code taskId} 的 SSE 流。调用前应已在请求线程内校验任务存在性（404 同步暴露）。
     *
     * @param tenantId 请求线程的租户，推送线程据此重建上下文
     */
    public SseEmitter open(long taskId, long tenantId) {
        SseEmitter emitter = new SseEmitter(MAX_DURATION_MS + 5_000);
        long deadline = System.currentTimeMillis() + MAX_DURATION_MS;
        AtomicBoolean done = new AtomicBoolean(false);

        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (done.get()) {
                    return;
                }
                try {
                    TaskSnapshot snap = TenantContext.callAs(tenantId, () -> taskService.get(taskId));
                    emitter.send(SseEmitter.event().data(TaskView.from(snap)));
                    if (snap.status().isTerminal() || System.currentTimeMillis() > deadline) {
                        finish(done, emitter);
                    } else {
                        scheduleNext(this, done);
                    }
                } catch (TaskException e) {
                    // 任务消失（被清理等）→ 结束流
                    finishWithError(done, emitter, e);
                } catch (IOException e) {
                    // 客户端断开
                    finish(done, emitter);
                } catch (RuntimeException e) {
                    log.warn("[task-sse] push failed taskId={}", taskId, e);
                    finishWithError(done, emitter, e);
                }
            }
        };

        emitter.onCompletion(() -> done.set(true));
        emitter.onTimeout(() -> finish(done, emitter));
        // 首帧立即推（虚拟线程），之后由 tick 自重排。
        worker.execute(tick);
        return emitter;
    }

    /** 排下一帧：scheduler 到点后把阻塞工作丢回虚拟线程执行器。 */
    private void scheduleNext(Runnable tick, AtomicBoolean done) {
        if (done.get()) {
            return;
        }
        scheduler.schedule(() -> worker.execute(tick), POLL_MS, TimeUnit.MILLISECONDS);
    }

    private static void finish(AtomicBoolean done, SseEmitter emitter) {
        if (done.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private static void finishWithError(AtomicBoolean done, SseEmitter emitter, Throwable e) {
        if (done.compareAndSet(false, true)) {
            emitter.completeWithError(e);
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        worker.shutdownNow();
    }
}
