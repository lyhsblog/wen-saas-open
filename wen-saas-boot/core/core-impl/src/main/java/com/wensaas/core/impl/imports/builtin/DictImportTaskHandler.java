package com.wensaas.core.impl.imports.builtin;

import com.wensaas.core.api.audit.AuditLogRecorder;
import com.wensaas.core.api.dict.DictAdminService;
import com.wensaas.core.api.file.FileService;
import com.wensaas.core.api.imports.ImportValidationResolver;
import com.wensaas.core.impl.imports.AbstractImportTaskHandler;
import com.wensaas.core.impl.imports.ImportValidationEngine;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;

import java.util.List;
import java.util.Map;

/**
 * 字典项导入执行处理器：逐行 {@code dictAdminService.createItem}。
 */
public class DictImportTaskHandler extends AbstractImportTaskHandler<Void> {

    private final DictAdminService dictService;

    public DictImportTaskHandler(ImportEntityRegistry entityRegistry,
                                 ImportValidationEngine engine,
                                 FileService fileService,
                                 List<ImportValidationResolver> resolvers,
                                 AuditLogRecorder auditRecorder,
                                 DictAdminService dictService) {
        super(entityRegistry, engine, fileService, resolvers, auditRecorder);
        this.dictService = dictService;
    }

    @Override
    public String handlerCode() {
        return SysImportEntities.DICT_HANDLER;
    }

    @Override
    protected String entityCode() {
        return SysImportEntities.DICT_CODE;
    }

    @Override
    protected void writeRow(Void state, Map<String, String> cells) {
        Integer sortOrder = parseIntOrNull(cells.get("sortOrder"));
        dictService.createItem(cells.get("groupCode"),
                new DictAdminService.CreateItemRequest(cells.get("code"), cells.get("name"),
                        null, sortOrder, null, null));
    }

    private static Integer parseIntOrNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
