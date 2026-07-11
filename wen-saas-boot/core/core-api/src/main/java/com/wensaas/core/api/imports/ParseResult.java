package com.wensaas.core.api.imports;

import java.util.List;

/**
 * 解析 + 全量预校验结果。
 *
 * <p>{@code preview} 仅含前 N 行（默认 200），按「错误优先 + 行号」排序；
 * 全量行落预览暂存，由 {@code sessionId} 句柄在确认导入时取回执行。
 *
 * @param sessionId  预览会话句柄（确认导入时回传）
 * @param entityCode 导入实体编码
 * @param fileName   原始文件名
 * @param total      总行数
 * @param passed     通过行数
 * @param failed     错误行数
 * @param preview    预览行（≤200，已排序、已按筛选）
 */
public record ParseResult(
        String sessionId,
        String entityCode,
        String fileName,
        int total,
        int passed,
        int failed,
        List<PreviewRow> preview) {

    public ParseResult {
        preview = preview == null ? List.of() : List.copyOf(preview);
    }

    /** 是否可进入执行阶段（全行通过才允许导入）。 */
    public boolean importable() {
        return failed == 0 && total > 0;
    }
}
