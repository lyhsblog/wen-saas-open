package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.auth.org.DeptAdminService;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.api.imports.PreviewRow;
import com.wensaas.core.impl.imports.AbstractImportTaskHandler;
import com.wensaas.core.impl.imports.ImportValidationEngine;
import com.wensaas.core.impl.imports.ImportValidationOutcome;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门导入执行处理器：解析上级部门名 → id 后 {@code deptAdminService.create}。
 *
 * <p>执行态（上级部门 name→id 映射）直接取引擎校验产物 {@code outcome.referenceIds("parentName")}，
 * 经 {@link #prepare} 产出，避免单例 handler 持有跨执行状态。
 */
public class DeptImportTaskHandler extends AbstractImportTaskHandler<Map<String, Long>> {

    private final DeptAdminService deptService;

    public DeptImportTaskHandler(ImportEntityRegistry entityRegistry,
                                 ImportValidationEngine engine,
                                 FileService fileService,
                                 List<ImportValidationResolver> resolvers,
                                 AuditLogRecorder auditRecorder,
                                 DeptAdminService deptService) {
        super(entityRegistry, engine, fileService, resolvers, auditRecorder);
        this.deptService = deptService;
    }

    @Override
    public String handlerCode() {
        return SysImportEntities.DEPT_HANDLER;
    }

    @Override
    protected String entityCode() {
        return SysImportEntities.DEPT_CODE;
    }

    @Override
    protected Map<String, Long> prepare(List<PreviewRow> okRows, ImportValidationOutcome outcome) {
        // 可变副本：writeRow 建档后回填同批次新建部门（前向声明的上级在映射外）
        return new HashMap<>(outcome.referenceIds("parentName"));
    }

    @Override
    protected void writeRow(Map<String, Long> deptNameToId, Map<String, String> cells) {
        String parentName = BuiltinImports.trimToNull(cells.get("parentName"));
        Long parentId = parentName == null ? null : deptNameToId.get(parentName);
        DeptAdminService.DeptView created = deptService.create(new DeptAdminService.CreateDeptRequest(
                parentId, cells.get("name"), BuiltinImports.parseIntOrNull(cells.get("sortOrder"))));
        // 回填同批次新建部门 name→id，供后续行「先建父再建子」解析上级（T12-02）。
        String self = BuiltinImports.trimToNull(cells.get("name"));
        if (self != null) {
            deptNameToId.put(self, created.id());
        }
    }
}
