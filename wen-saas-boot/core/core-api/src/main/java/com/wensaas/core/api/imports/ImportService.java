package com.wensaas.core.api.imports;

import com.wensaas.core.api.spi.def.ImportEntityDef;

import java.io.InputStream;
import java.util.List;

/**
 * 数据导入框架对外门面。
 *
 * <p>承接导入端到端流程：列出实体 → 下载模板 → 上传解析预校验 → 重取预览 / 下载错误明细 →
 * 确认导入（提交任务中心执行）。所有方法隐含 {@code TenantContext.require()}。
 */
public interface ImportService {

    /** 当前可导入实体（前端「导入」入口渲染用）。 */
    List<ImportEntityDef> listEntities();

    /** 按字段定义生成 xlsx 模板（含示例行）。 */
    byte[] generateTemplate(String entityCode);

    /**
     * 上传解析 + 全量预校验，落预览暂存。
     *
     * @throws ImportException ENTITY_UNKNOWN / FILE_CORRUPT / EMPTY_FILE / TEMPLATE_MISMATCH / ROW_LIMIT_EXCEEDED
     */
    ParseResult parse(String entityCode, String fileName, InputStream content);

    /**
     * 重取预览（分页筛选）。
     *
     * @throws ImportException SESSION_NOT_FOUND
     */
    ParseResult getSession(String sessionId, PreviewFilter filter);

    /**
     * 生成错误明细 xlsx（原数据 + 错误说明）。
     *
     * @throws ImportException SESSION_NOT_FOUND
     */
    byte[] generateErrorReport(String sessionId);

    /**
     * 确认导入：全行通过才提交任务中心，返回 taskId。
     *
     * @throws ImportException SESSION_NOT_FOUND / HAS_ERRORS
     */
    Long confirm(String sessionId);
}
