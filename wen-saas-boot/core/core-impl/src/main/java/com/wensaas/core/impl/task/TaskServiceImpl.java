package com.wensaas.core.impl.task;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.request.RequestContext;
import com.wensaas.core.api.task.SubmitRequest;
import com.wensaas.core.api.task.TaskException;
import com.wensaas.core.api.task.TaskPage;
import com.wensaas.core.api.task.TaskQuery;
import com.wensaas.core.api.task.TaskSnapshot;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.task.TaskStatus;
import com.wensaas.core.api.task.TaskType;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.spi.registry.TaskHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.wensaas.core.impl.task.execution.TaskExecutionMessage;
import com.wensaas.core.impl.task.execution.TaskExecutionPublisher;
import com.wensaas.core.impl.task.progress.TaskProgressStore;

public class TaskServiceImpl implements TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final SysTaskMapper mapper;
    private final TaskHandlerRegistry registry;
    private final TaskProgressStore progressStore;
    private final TaskExecutionPublisher publisher;
    private final TaskProperties props;

    public TaskServiceImpl(SysTaskMapper mapper,
                            TaskHandlerRegistry registry,
                            TaskProgressStore progressStore,
                            TaskExecutionPublisher publisher,
                            TaskProperties props) {
        this.mapper = mapper;
        this.registry = registry;
        this.progressStore = progressStore;
        this.publisher = publisher;
        this.props = props;
    }

    @Override
    public Long submit(SubmitRequest req) {
        long tenantId = TenantContext.require();
        UserPrincipal principal = UserContext.require();

        if (registry.findByKey(req.handlerCode().toLowerCase()).isEmpty()) {
            throw new TaskException(TaskException.Code.HANDLER_NOT_FOUND,
                    "handlerCode 未注册: " + req.handlerCode());
        }
        if (mapper.countRunningByHandler(tenantId, req.handlerCode()) > 0) {
            throw new TaskException(TaskException.Code.DUPLICATE_RUNNING_TASK,
                    "已有同 handler 的任务在运行: " + req.handlerCode());
        }

        SysTask t = new SysTask();
        t.setTenantId(tenantId);
        t.setTaskType(req.type().name());
        t.setTaskName(req.name());
        t.setHandlerCode(req.handlerCode());
        t.setTaskParams(req.params());
        t.setStatus(TaskStatus.PENDING.name());
        t.setProgress(0);
        t.setCreatorId(principal.userId());
        LocalDateTime now = LocalDateTime.now();
        t.setCreatedAt(now);
        t.setExpiresAt(now.plus(props.getExpiresTtl()));
        mapper.insert(t);

        TaskExecutionMessage msg = new TaskExecutionMessage(t.getId(), tenantId, RequestContext.get());
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 事务已提交，此处发布失败不能再回滚 INSERT；记 error 后兜底交由
                    // PendingTaskReaperJob 周期补偿重投（M8，本地消息表语义），不再需要人工干预
                    try {
                        publisher.publish(msg);
                    } catch (RuntimeException e) {
                        log.error("task.submit afterCommit MQ 发布失败，任务暂卡 PENDING，待补偿巡检重投 taskId={} tenantId={}",
                                msg.taskId(), tenantId, e);
                    }
                }
            });
        } else {
            publisher.publish(msg);
        }
        log.info("task.submit ok taskId={} tenantId={} handler={}",
                t.getId(), tenantId, req.handlerCode());
        return t.getId();
    }

    @Override
    public TaskSnapshot get(Long taskId) {
        SysTask t = mapper.selectById(taskId);
        if (t == null) {
            throw new TaskException(TaskException.Code.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }
        return toSnapshot(t);
    }

    @Override
    public void cancel(Long taskId) {
        SysTask t = mapper.selectById(taskId);
        if (t == null) {
            throw new TaskException(TaskException.Code.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }
        if (!TaskStatus.RUNNING.name().equals(t.getStatus())) {
            throw new TaskException(TaskException.Code.CANCEL_NOT_ALLOWED,
                    "仅 RUNNING 任务可取消，当前: " + t.getStatus());
        }
        t.setStatus(TaskStatus.CANCELLING.name());
        mapper.updateById(t);
        progressStore.markCancelled(taskId);
        log.info("task.cancel ok taskId={}", taskId);
    }

    @Override
    public List<TaskSnapshot> listByCreator(Long creatorId, int offset, int limit) {
        long tenantId = TenantContext.require();
        List<SysTask> rows = mapper.selectByCreator(tenantId, creatorId, offset, limit);
        return rows.stream().map(this::toSnapshot).toList();
    }

    @Override
    public TaskPage queryMine(Long creatorId, TaskQuery query) {
        long tenantId = TenantContext.require();
        String status = query.status() == null ? null : query.status().name();
        String type = query.type() == null ? null : query.type().name();
        long total = mapper.countMine(tenantId, creatorId, status, type);
        if (total == 0) {
            return new TaskPage(List.of(), 0);
        }
        List<SysTask> rows = mapper.selectMine(tenantId, creatorId, status, type,
                query.offset(), query.limit());
        return new TaskPage(rows.stream().map(this::toSnapshot).toList(), total);
    }

    @Override
    public boolean isCancelled(Long taskId) {
        return progressStore.isCancelled(taskId);
    }

    private TaskSnapshot toSnapshot(SysTask t) {
        Optional<TaskProgressStore.TaskProgress> live = progressStore.readProgress(t.getId());
        int progress = live.map(TaskProgressStore.TaskProgress::progress).orElseGet(() -> nullSafe(t.getProgress()));
        Integer processed = live.map(TaskProgressStore.TaskProgress::processed).orElse(t.getProcessedCount());
        Integer total = live.map(TaskProgressStore.TaskProgress::total).orElse(t.getTotalCount());
        Integer success = live.map(TaskProgressStore.TaskProgress::success).orElse(t.getSuccessCount());
        Integer failed = live.map(TaskProgressStore.TaskProgress::failed).orElse(t.getFailedCount());
        return new TaskSnapshot(
                t.getId(),
                t.getTenantId(),
                TaskType.valueOf(t.getTaskType()),
                t.getTaskName(),
                t.getHandlerCode(),
                TaskStatus.valueOf(t.getStatus()),
                progress,
                total,
                processed,
                success,
                failed,
                t.getResultData(),
                t.getErrorMessage(),
                t.getCreatorId(),
                t.getCreatedAt(),
                t.getStartedAt(),
                t.getFinishedAt(),
                t.getExpiresAt());
    }

    private static int nullSafe(Integer i) {
        return i == null ? 0 : i;
    }
}
