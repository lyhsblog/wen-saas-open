package com.wensaas.core.impl.task;

import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.impl.spi.registry.TaskHandlerRegistry;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.wensaas.core.impl.task.execution.RocketMqTaskExecutionPublisher;
import com.wensaas.core.impl.task.execution.TaskExecutionConsumer;
import com.wensaas.core.impl.task.execution.TaskExecutionPublisher;
import com.wensaas.core.impl.task.execution.TaskRunner;
import com.wensaas.core.impl.task.progress.RedisTaskProgressStore;
import com.wensaas.core.impl.task.progress.TaskProgressStore;

/**
 * 任务中心装配。
 *
 * <p>{@code wen-saas-boot.task.enabled=true}（默认）装配完整管道；关闭时整体不生效（业务方不能注 TaskService）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TaskProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.task", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class TaskConfiguration {

    @Bean
    public TaskProgressStore taskProgressStore(StringRedisTemplate redis, TaskProperties props) {
        return new RedisTaskProgressStore(redis, props);
    }

    @Bean
    @ConditionalOnMissingBean(TaskExecutionPublisher.class)
    public TaskExecutionPublisher taskExecutionPublisher(ObjectProvider<RocketMQClientTemplate> templateProvider,
                                                          TaskProperties props) {
        // RocketMQTemplate 可能未装配（rocketmq autoconfig 缺失，仅测试环境）；用 ObjectProvider 兜底
        // @ConditionalOnMissingBean：让集成测试可注入 noop publisher 覆盖
        return new RocketMqTaskExecutionPublisher(templateProvider, props);
    }

    @Bean
    public TaskRunner taskRunner(SysTaskMapper mapper,
                                  TaskHandlerRegistry registry,
                                  TaskProgressStore progressStore,
                                  ApplicationContext applicationContext,
                                  TaskProperties props,
                                  ObjectProvider<MessageService> messageServiceProvider) {
        return new TaskRunner(mapper, registry, progressStore, applicationContext, props, messageServiceProvider);
    }

    @Bean
    public TaskService taskService(SysTaskMapper mapper,
                                    TaskHandlerRegistry registry,
                                    TaskProgressStore progressStore,
                                    TaskExecutionPublisher publisher,
                                    TaskProperties props) {
        return new TaskServiceImpl(mapper, registry, progressStore, publisher, props);
    }

    @Bean
    @ConditionalOnProperty(name = "wen-saas-boot.mq.consumers-enabled",
            havingValue = "true", matchIfMissing = true)
    public TaskExecutionConsumer taskExecutionConsumer(TaskRunner runner,
                                                        com.wensaas.core.impl.tenant.web.TenantActivityGuard activityGuard,
                                                        com.wensaas.core.api.idempotent.IdempotencyGuard idempotency,
                                                        TaskProperties props,
                                                        com.wensaas.core.api.mq.MqConsumeSupport mqSupport) {
        return new TaskExecutionConsumer(runner, activityGuard, idempotency, props, mqSupport);
    }
}
