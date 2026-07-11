package com.wensaas.core.api.task;

import java.util.List;

/**
 * 任务中心对外服务。
 *
 * <p>调用约束：所有方法读写都隐含 {@code TenantContext.require()}；运营场景须 set 平台租户 0。
 */
public interface TaskService {

    /**
     * 提交任务。
     *
     * <p>校验：handlerCode 已注册；同租户同 handler 不允许并发 RUNNING；落 PENDING 后直接投递 MQ。
     *
     * @return 新建任务 ID
     * @throws TaskException 含 HANDLER_NOT_FOUND / DUPLICATE_RUNNING_TASK
     */
    Long submit(SubmitRequest request);

    /**
     * 查询任务。隐含按 tenant_id 过滤；不存在或跨租户都抛 TASK_NOT_FOUND。
     *
     * <p>进度、计数等运行时字段优先读 Redis，DB 兜底。
     */
    TaskSnapshot get(Long taskId);

    /**
     * 用户取消（仅 RUNNING 可取消）。
     * 在 sys_task 设 status=CANCELLING + Redis 写 cancel 标志；handler 周期性 isCancelled 后退出。
     *
     * @throws TaskException CANCEL_NOT_ALLOWED 当前状态非 RUNNING
     */
    void cancel(Long taskId);

    /** 按提交人列出（按 created_at DESC，offset/limit 分页）。 */
    List<TaskSnapshot> listByCreator(Long creatorId, int offset, int limit);

    /**
     * 按提交人分页查询（可选 status / type 过滤），返回记录 + 真实总数。
     * 隐含按 {@code TenantContext.require()} 过滤。供任务中心列表使用。
     */
    TaskPage queryMine(Long creatorId, TaskQuery query);

    /** Handler 长循环内调用：当前任务是否已被取消。 */
    boolean isCancelled(Long taskId);
}
