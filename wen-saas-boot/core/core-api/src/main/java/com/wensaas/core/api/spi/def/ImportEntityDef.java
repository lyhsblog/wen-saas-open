package com.wensaas.core.api.spi.def;

import java.util.List;

/**
 * 数据导入实体贡献项。
 *
 * <p>业务模块经 {@code BusinessModuleDescriptor.importEntities()} 声明；底座据 {@link #fields}
 * 生成模板、解析上传、执行基础校验，并把「确认导入」提交到 {@link #taskHandlerCode} 绑定的任务处理器。
 *
 * @param code            导入实体编码，按 {@code {module}.{entity}} 命名，全局唯一（如 {@code sys.member}）
 * @param name            显示名
 * @param taskHandlerCode 执行阶段绑定的任务处理器编码（如 {@code sys.task.import.member}）
 * @param fields          模板字段定义（顺序即模板列顺序）
 * @param rowLimit        单文件行数上限（默认 5000）
 * @param needPreview     是否需预览确认（默认 true；false=直接上传执行）
 */
public record ImportEntityDef(
        String code,
        String name,
        String taskHandlerCode,
        List<ImportFieldDef> fields,
        int rowLimit,
        boolean needPreview) {

    /** 默认单文件行数上限。 */
    public static final int DEFAULT_ROW_LIMIT = 5000;

    public ImportEntityDef {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("ImportEntityDef.code 不可为空");
        }
        if (taskHandlerCode == null || taskHandlerCode.isBlank()) {
            throw new IllegalArgumentException("ImportEntityDef.taskHandlerCode 不可为空");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
        if (rowLimit <= 0) {
            rowLimit = DEFAULT_ROW_LIMIT;
        }
    }

    /** 取默认行数上限 + 需预览的常规构造。 */
    public static ImportEntityDef of(String code, String name, String taskHandlerCode,
                                     List<ImportFieldDef> fields) {
        return new ImportEntityDef(code, name, taskHandlerCode, fields, DEFAULT_ROW_LIMIT, true);
    }
}
