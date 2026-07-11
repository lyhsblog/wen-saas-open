package com.wensaas.core.impl.compliance;

import com.wensaas.core.api.compliance.ComplianceService;
import com.wensaas.core.api.compliance.export.DataExportService;
import com.wensaas.core.api.compliance.encryption.EncryptionService;
import com.wensaas.core.api.compliance.OpsComplianceService;
import com.wensaas.core.api.compliance.retention.RetentionService;
import com.wensaas.core.api.compliance.spi.DataExportContributor;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.ops.access.OpsAccessContextResolver;
import com.wensaas.core.api.task.TaskService;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.core.impl.compliance.export.DataExportTaskHandler;
import com.wensaas.core.impl.exports.ExportTaskSubmitter;
import com.wensaas.core.impl.file.SysFileMapper;
import com.wensaas.core.impl.permission.eval.PermissionResolver;
import com.wensaas.core.impl.security.SensitiveFieldCipher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;
import com.wensaas.core.impl.compliance.export.DataExportRequestMapper;
import com.wensaas.core.impl.compliance.export.DataExportServiceImpl;
import com.wensaas.core.impl.compliance.export.ExportApprovalRuleMapper;
import com.wensaas.core.impl.compliance.export.ExportObjectRegistry;
import com.wensaas.core.impl.compliance.export.ExportPolicyMapper;
import com.wensaas.core.impl.compliance.export.ExportRequestMapper;

/**
 * 数据隐私与合规模块 Bean 装配。
 *
 * <p>{@code ComplianceSupport} / 描述符 / 各 {@link DataExportContributor} 为 {@code @Component}，
 * 此处装配 Service 入口与全量导出异步打包 handler。
 */
@Configuration(proxyBeanMethods = false)
public class ComplianceConfiguration {

    @Bean
    public ComplianceService complianceService(ExportRequestMapper requestMapper,
                                               ExportPolicyMapper policyMapper,
                                               ExportApprovalRuleMapper ruleMapper,
                                               ExportObjectRegistry objectRegistry,
                                               ComplianceSupport support,
                                               TenantQueryService tenantQuery,
                                               PermissionResolver permissionResolver,
                                               ObjectProvider<ExportTaskSubmitter> exportTaskSubmitterProvider) {
        // ObjectProvider：导出执行框架与任务中心同条件装配，task 关闭的测试上下文可缺席；
        // 同时打断 ExportServiceImpl → ComplianceService → ExportTaskSubmitter 的 bean 环。
        return new ComplianceServiceImpl(requestMapper, policyMapper, ruleMapper, objectRegistry,
                support, tenantQuery, permissionResolver, exportTaskSubmitterProvider);
    }

    @Bean
    public DataExportService dataExportService(DataExportRequestMapper requestMapper,
                                               ComplianceSupport support,
                                               @Lazy TaskService taskService,
                                               FileService fileService) {
        // @Lazy：TaskService 仅在 submit() 运行期使用；惰性注入让 task 关闭的测试上下文（core-impl
        // 集成测试默认 task.enabled=false）也能完成装配，不再因缺 TaskService 整体 context 加载失败。
        return new DataExportServiceImpl(requestMapper, support, taskService, fileService);
    }

    @Bean
    public DataExportTaskHandler dataExportTaskHandler(DataExportRequestMapper requestMapper,
                                                       List<DataExportContributor> contributors,
                                                       FileService fileService,
                                                       ComplianceSupport support) {
        return new DataExportTaskHandler(requestMapper, contributors, fileService, support);
    }

    @Bean
    public RetentionService retentionService(SysFileMapper fileMapper) {
        return new RetentionServiceImpl(fileMapper);
    }

    @Bean
    public EncryptionService encryptionService(SensitiveFieldCipher sensitiveFieldCipher) {
        return new EncryptionServiceImpl(sensitiveFieldCipher);
    }

    @Bean
    public OpsComplianceService opsComplianceService(ExportRequestMapper requestMapper,
                                                     ComplianceSupport support,
                                                     TenantQueryService tenantQuery,
                                                     OpsAccessContextResolver accessResolver) {
        return new OpsComplianceServiceImpl(requestMapper, support, tenantQuery, accessResolver);
    }
}
