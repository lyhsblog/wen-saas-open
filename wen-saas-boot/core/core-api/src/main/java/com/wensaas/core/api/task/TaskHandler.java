package com.wensaas.core.api.task;

/**
 * 业务模块任务处理器 SPI。
 *
 * <p>实现要求：
 * <ul>
 *   <li>{@code @Component} 标注，由业务模块自己的 Spring 上下文管理生命周期</li>
 *   <li>通过 {@code BusinessModuleDescriptor.taskHandlers()} 声明绑定的 handlerCode</li>
 *   <li>handlerCode 命名约定：业务模块表前缀 + {@code .task.} + 业务标识，如 {@code crm.task.import.customer}</li>
 *   <li>{@link #execute(TaskContext)} 内通过 {@link TaskContext} 上报进度、检测取消、设置结果</li>
 * </ul>
 */
public interface TaskHandler {

    /** 处理器标识。必须与 {@code BusinessModuleDescriptor.taskHandlers()} 中声明的一致。 */
    String handlerCode();

    /**
     * 执行任务。
     *
     * <p>正常结束自动 status=COMPLETED；抛 RuntimeException 自动 status=FAILED；
     * 检测到 {@link TaskContext#isCancelled()} 应优雅退出并直接 return（runner 会标 CANCELLED）。
     */
    void execute(TaskContext context);

    /**
     * 是否由 handler 自行负责终态通知。
     *
     * <p>默认 false：TaskRunner 在 COMPLETED / FAILED 终态自动给发起人投通用站内信
     * （{@code sys.task.completed} / {@code sys.task.failed}）。handler 若已发送更贴切的
     * 专用通知（如导出任务的带下载链接模板），应返回 true 抑制通用通知，避免发起人收到重复消息。
     */
    default boolean suppressTerminalNotification() {
        return false;
    }
}
