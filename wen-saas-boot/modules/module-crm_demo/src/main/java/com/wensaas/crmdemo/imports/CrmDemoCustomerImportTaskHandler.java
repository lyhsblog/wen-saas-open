package com.wensaas.crmdemo.imports;

import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.auth.user.UserPrincipal;
import com.wensaas.core.api.auth.user.UserQueryService;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.api.task.TaskContext;
import com.wensaas.core.impl.imports.AbstractImportTaskHandler;
import com.wensaas.core.impl.imports.ImportValidationEngine;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;
import com.wensaas.crmdemo.entity.CustomerStatus;
import com.wensaas.crmdemo.service.CrmDemoCustomerService;
import com.wensaas.crmdemo.spi.CrmDemoModule;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户导入执行器。基类封装「取文件→解析→重校验→逐行→失败明细回报」，本类只做三件事：
 * 恢复申请人上下文（{@code service.create} 依赖 {@code UserContext}，任务线程默认只有租户上下文，
 * operator 经提交参数透传——照导出 handler 同款范式）、{@link #prepare} 建 token→值映射、
 * {@link #writeRow} 走正常业务链建档（自定义字段校验/领域事件照常，负责人分配通知按批量语义抑制）。
 */
@Component
public class CrmDemoCustomerImportTaskHandler
        extends AbstractImportTaskHandler<CrmDemoCustomerImportTaskHandler.State> {

    private final CrmDemoCustomerService customerService;
    private final DictService dictService;
    private final UserQueryService userQuery;

    public CrmDemoCustomerImportTaskHandler(ImportEntityRegistry entityRegistry,
                                            ImportValidationEngine engine,
                                            FileService fileService,
                                            List<ImportValidationResolver> resolvers,
                                            AuditLogRecorder auditRecorder,
                                            CrmDemoCustomerService customerService,
                                            DictService dictService,
                                            UserQueryService userQuery) {
        super(entityRegistry, engine, fileService, resolvers, auditRecorder);
        this.customerService = customerService;
        this.dictService = dictService;
        this.userQuery = userQuery;
    }

    @Override
    public String handlerCode() {
        return CrmDemoModule.IMPORT_CUSTOMER_HANDLER;
    }

    @Override
    protected String entityCode() {
        return CrmDemoModule.IMPORT_CUSTOMER;
    }

    @Override
    public void execute(TaskContext ctx) {
        long operatorId = Long.parseLong(String.valueOf(ctx.params().get("operatorId")));
        String operatorName = String.valueOf(ctx.params().getOrDefault("operatorName", "import"));
        UserPrincipal applicant = new UserPrincipal(operatorId, ctx.tenantId(), operatorName,
                "import:" + ctx.taskId(), false, null, false, null);
        UserContext.runAs(applicant, () -> super.execute(ctx));
    }

    @Override
    protected State prepare(List<PreviewRow> okRows) {
        // 不消费引擎校验产物：outcome 的 source 映射是 token→项 id，写库需要项 code，自建映射
        // 来源 token（code 或名称）→ 项 code
        Map<String, String> sourceCodeByToken = new HashMap<>();
        for (DictItem item : dictService.listItems(CrmDemoModule.DICT_CUSTOMER_SOURCE)) {
            if (item.code() != null) {
                sourceCodeByToken.put(item.code(), item.code());
                if (item.name() != null) {
                    sourceCodeByToken.put(item.name(), item.code());
                }
            }
        }
        // 负责人联系方式 → userId（distinct 后逐个定位，校验期已保证可定位）
        Map<String, Long> ownerByContact = new HashMap<>();
        for (PreviewRow row : okRows) {
            String owner = row.cells().get("owner");
            if (owner != null && !owner.isBlank() && !ownerByContact.containsKey(owner.trim())) {
                var user = userQuery.userByContact(owner.trim());
                if (user != null) {
                    ownerByContact.put(owner.trim(), user.id());
                }
            }
        }
        return new State(sourceCodeByToken, ownerByContact);
    }

    @Override
    protected void writeRow(State state, Map<String, String> cells) {
        String statusToken = cells.get("status");
        CustomerStatus status = CustomerStatus.parseFlexible(statusToken);
        String sourceToken = cells.get("source");
        String sourceCode = sourceToken == null || sourceToken.isBlank()
                ? null : state.sourceCodeByToken().get(sourceToken.trim());
        String ownerToken = cells.get("owner");
        Long ownerId = ownerToken == null || ownerToken.isBlank()
                ? null : state.ownerByContact().get(ownerToken.trim());

        customerService.create(new CrmDemoCustomerService.CreateCommand(
                cells.get("name"), blankToNull(cells.get("code")),
                status == null ? null : status.name(), sourceCode,
                ownerId, null, null), false);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    record State(Map<String, String> sourceCodeByToken, Map<String, Long> ownerByContact) {
    }
}
