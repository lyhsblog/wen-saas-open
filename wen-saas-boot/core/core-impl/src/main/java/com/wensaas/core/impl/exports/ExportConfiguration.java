package com.wensaas.core.impl.exports;

import com.wensaas.core.api.auth.permission.MemberAdminService;
import com.wensaas.core.api.compliance.ComplianceService;
import com.wensaas.core.api.dict.DictAdminService;
import com.wensaas.core.api.exports.spi.ExportDataProvider;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.tenant.audit.TenantOperationLogQueryService;
import com.wensaas.core.impl.exports.builtin.AuditLogExportProvider;
import com.wensaas.core.impl.exports.builtin.DictExportProvider;
import com.wensaas.core.impl.exports.builtin.MemberExportProvider;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;

/**
 * 业务数据导出框架装配。
 *
 * <p>与导入框架同条件（{@code wen-saas-boot.task.enabled}）：执行依赖任务中心，
 * 任务关闭时整体不装配。内置三个 provider 与 {@code BaseExportObjectContributor}
 * 声明的导出对象一一对应（member / audit_log / dict）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wen-saas-boot.task", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ExportConfiguration {

    @Bean
    public MemberExportProvider memberExportProvider(MemberAdminService memberService) {
        return new MemberExportProvider(memberService);
    }

    @Bean
    public AuditLogExportProvider auditLogExportProvider(TenantOperationLogQueryService queryService) {
        return new AuditLogExportProvider(queryService);
    }

    @Bean
    public DictExportProvider dictExportProvider(DictAdminService dictService) {
        return new DictExportProvider(dictService);
    }

    @Bean
    public ExportServiceImpl exportService(List<ExportDataProvider> providers,
                                           ComplianceService complianceService,
                                           @Lazy TaskService taskService,
                                           PermissionResolver permissionResolver) {
        // @Lazy TaskService：与 DataExportService 同理，仅 submit() 运行期使用
        return new ExportServiceImpl(providers, complianceService, taskService, permissionResolver);
    }

    @Bean
    public ExportTaskHandler exportTaskHandler(List<ExportDataProvider> providers,
                                               FileService fileService,
                                               ObjectProvider<MessageService> messageServiceProvider) {
        return new ExportTaskHandler(providers, fileService, messageServiceProvider);
    }
}
