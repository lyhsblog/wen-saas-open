package com.wensaas.core.api.compliance.export;

import com.wensaas.core.api.compliance.ComplianceException;
/**
 * 客户全量数据导出（GDPR-style）服务（租户上下文）。
 *
 * <p>治理规则：仅主管理员可发起、必填理由、每租户月限 1 次、敏感字段完整 / 脱敏二选一；
 * 全过程作为敏感操作记录到操作日志（Web 层 {@code @Auditable}）。打包完成后站内信通知
 * 主管理员，下载链接 7 天有效。
 */
public interface DataExportService {

    /** 发起全量数据导出（仅主管理员；超月限 / 非主管理员抛 {@link ComplianceException}）。 */
    DataExportView submit(SubmitCommand cmd);

    /** 本租户全量导出申请列表（倒序）。 */
    DataExportPage list(int page, int size);

    /** 申请详情（主管理员 / 发起人可见）。 */
    DataExportView detail(long id);

    /**
     * 换取导出包下载 URL（主管理员 / 发起人可见）。校验：已完成、文件已落库、下载链接未过期
     * （完成后 7 天）；通过后返回文件中心签名下载 URL。
     */
    String downloadUrl(long id);

    /** 发起命令。 */
    record SubmitCommand(String reason, DataExportMode dataMode) {
    }
}
