package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.auth.permission.MemberAdminService;
import com.wensaas.core.api.dict.DictAdminService;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.impl.imports.ImportValidationEngine;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 内置导入实体的 handler / resolver 装配。
 *
 * <p>与任务中心 + 导入框架同条件（{@code wen-saas-boot.task.enabled}）：handler 执行依赖任务中心，
 * resolver 注入导入框架；任务关闭时整体不装配（SPI 集成测试上下文不强求各域 admin service）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wen-saas-boot.task", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SysImportConfiguration {

    // ---- 字典 ----
    @Bean
    public DictImportValidationResolver dictImportValidationResolver(DictAdminService dictService) {
        return new DictImportValidationResolver(dictService);
    }

    @Bean
    public DictImportTaskHandler dictImportTaskHandler(ImportEntityRegistry entityRegistry,
                                                       ImportValidationEngine engine,
                                                       FileService fileService,
                                                       List<ImportValidationResolver> resolvers,
                                                       ObjectProvider<AuditLogRecorder> auditRecorder,
                                                       DictAdminService dictService) {
        return new DictImportTaskHandler(entityRegistry, engine, fileService, resolvers,
                auditRecorder.getIfAvailable(), dictService);
    }

    // ---- 部门 ----
    @Bean
    public DeptImportValidationResolver deptImportValidationResolver(DeptAdminService deptService) {
        return new DeptImportValidationResolver(deptService);
    }

    @Bean
    public DeptImportTaskHandler deptImportTaskHandler(ImportEntityRegistry entityRegistry,
                                                       ImportValidationEngine engine,
                                                       FileService fileService,
                                                       List<ImportValidationResolver> resolvers,
                                                       ObjectProvider<AuditLogRecorder> auditRecorder,
                                                       DeptAdminService deptService) {
        return new DeptImportTaskHandler(entityRegistry, engine, fileService, resolvers,
                auditRecorder.getIfAvailable(), deptService);
    }

    // ---- 成员 ----
    @Bean
    public MemberImportValidationResolver memberImportValidationResolver(DeptAdminService deptService) {
        return new MemberImportValidationResolver(deptService);
    }

    @Bean
    public MemberImportTaskHandler memberImportTaskHandler(ImportEntityRegistry entityRegistry,
                                                           ImportValidationEngine engine,
                                                           FileService fileService,
                                                           List<ImportValidationResolver> resolvers,
                                                           ObjectProvider<AuditLogRecorder> auditRecorder,
                                                           MemberAdminService memberService) {
        return new MemberImportTaskHandler(entityRegistry, engine, fileService, resolvers,
                auditRecorder.getIfAvailable(), memberService);
    }
}
