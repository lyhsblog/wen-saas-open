package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.auth.permission.MemberAdminService;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.impl.imports.AbstractImportTaskHandler;
import com.wensaas.core.impl.imports.ImportValidationEngine;
import com.wensaas.core.impl.imports.ImportValidationOutcome;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;

import java.util.List;
import java.util.Map;

/**
 * 成员导入执行处理器：解析部门名 → id 后 {@code memberAdminService.createMember}。
 *
 * <p>账号 / 手机 / 邮箱加密落库、唯一性、激活流均由 {@code MemberAdminService} 内部处理；
 * 冲突时其抛 {@code MemberAdminException} → 基类捕获记失败 + 入结果明细。
 */
public class MemberImportTaskHandler extends AbstractImportTaskHandler<Map<String, Long>> {

    private final MemberAdminService memberService;

    public MemberImportTaskHandler(ImportEntityRegistry entityRegistry,
                                   ImportValidationEngine engine,
                                   FileService fileService,
                                   List<ImportValidationResolver> resolvers,
                                   AuditLogRecorder auditRecorder,
                                   MemberAdminService memberService) {
        super(entityRegistry, engine, fileService, resolvers, auditRecorder);
        this.memberService = memberService;
    }

    @Override
    public String handlerCode() {
        return SysImportEntities.MEMBER_HANDLER;
    }

    @Override
    protected String entityCode() {
        return SysImportEntities.MEMBER_CODE;
    }

    @Override
    protected Map<String, Long> prepare(List<PreviewRow> okRows, ImportValidationOutcome outcome) {
        return outcome.referenceIds("deptName");
    }

    @Override
    protected void writeRow(Map<String, Long> deptNameToId, Map<String, String> cells) {
        Long mainDeptId = deptNameToId.get(cells.get("deptName"));
        memberService.createMember(new MemberAdminService.CreateMemberCommand(
                cells.get("username"),
                cells.get("realName"),
                BuiltinImports.trimToNull(cells.get("mobile")),
                BuiltinImports.trimToNull(cells.get("email")),
                mainDeptId,
                List.of(),
                List.of(),
                null,
                null));
    }
}
