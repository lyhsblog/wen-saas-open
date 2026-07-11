package com.wensaas.core.impl.async;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

/**
 * 异步上下文传递装配。
 *
 * <p>启用 {@code @EnableAsync}；用虚拟线程 executor 作为默认 taskExecutor；通过
 * {@link ContextPropagatingTaskDecorator} 让每个 @Async 提交无感传递四件套
 * （UserContext + TenantContext + RequestContext + MDC）。
 *
 * <p>JDK 25 + Spring Boot 4，{@code spring.threads.virtual.enabled=true} 已开；本配置覆盖默认
 * {@code applicationTaskExecutor} bean，确保 TaskDecorator 生效（默认 bean 不应用 decorator）。
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncContextConfiguration {

    @Bean
    public TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }

    /**
     * Spring {@code @Async} 默认会找名为 {@code taskExecutor} 的 bean；覆盖之，应用 decorator。
     * 用虚拟线程 per-task executor（Loom），无线程池上限，适合 IO 密集型任务。
     */
    @Bean(name = "taskExecutor")
    public AsyncTaskExecutor taskExecutor(TaskDecorator contextPropagatingTaskDecorator) {
        TaskExecutorAdapter adapter = new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
        adapter.setTaskDecorator(contextPropagatingTaskDecorator);
        return adapter;
    }
}
