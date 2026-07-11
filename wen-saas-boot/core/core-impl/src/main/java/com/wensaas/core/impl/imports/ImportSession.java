package com.wensaas.core.impl.imports;

import com.wensaas.core.api.imports.PreviewRow;

import java.util.List;

/**
 * 预览会话（落预览暂存）。
 *
 * <p>**不在 Redis 存全量行**——原始上传文件落文件中心（{@link #fileId}），Redis 只存
 * 元数据 + 轻量预览（错误 / 通过各 ≤200 行，供三种筛选即时显示，免重解析）。错误明细下载与
 * 确认执行（handler）按 {@link #fileId} 重读原文件 + 重校验，故执行期不依赖本会话存活。
 *
 * @param fileId       原始上传文件在文件中心的 id（执行/错误明细据此重读）
 * @param errorPreview 错误行预览（≤200，错误优先）
 * @param passedPreview 通过行预览（≤200）
 */
public record ImportSession(
        String sessionId,
        long tenantId,
        long creatorId,
        String entityCode,
        String entityName,
        String taskHandlerCode,
        String fileName,
        String fileId,
        int total,
        int passed,
        int failed,
        List<PreviewRow> errorPreview,
        List<PreviewRow> passedPreview) {
}
