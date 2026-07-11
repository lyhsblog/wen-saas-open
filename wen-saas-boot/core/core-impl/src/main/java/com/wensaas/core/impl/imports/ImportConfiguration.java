package com.wensaas.core.impl.imports;

import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.imports.ImportService;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * 数据导入框架装配。
 *
 * <p>与任务中心同条件（{@code wen-saas-boot.task.enabled}，默认 true）：导入执行依赖 {@link TaskService}，
 * 任务中心关闭时整套导入框架不装配（测试默认关闭，故 SPI 集成测试上下文不强求 TaskService）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wen-saas-boot.task", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ImportConfiguration {

    @Bean
    public ImportValidationEngine importValidationEngine() {
        return new ImportValidationEngine();
    }

    @Bean
    public ImportSessionStore importSessionStore(StringRedisTemplate redis) {
        return new ImportSessionStore(redis);
    }

    @Bean
    public ImportService importService(ImportEntityRegistry registry,
                                       ImportValidationEngine engine,
                                       ImportSessionStore store,
                                       TaskService taskService,
                                       FileService fileService,
                                       List<ImportValidationResolver> resolvers) {
        return new ImportServiceImpl(registry, engine, store, taskService, fileService, resolvers);
    }
}
