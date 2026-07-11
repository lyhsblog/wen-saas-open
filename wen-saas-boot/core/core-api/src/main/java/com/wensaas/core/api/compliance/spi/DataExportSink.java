package com.wensaas.core.api.compliance.spi;

import com.wensaas.core.api.compliance.export.DataExportMode;

import java.nio.charset.StandardCharsets;

/**
 * 全量数据导出（GDPR-style）打包收集器：{@link DataExportContributor} 通过本接口
 * 向导出包写入条目，并登记概要计数。
 *
 * <p>条目路径相对各 contributor 的 {@link DataExportContributor#section()} 子目录（如成员贡献器
 * 写 {@code members.json}，最终落在 zip 内 {@code members/members.json}）。实现由底座提供，
 * contributor 不持有 zip 句柄，避免误关流 / 越界写。
 */
public interface DataExportSink {

    /** 当前导出所属租户。 */
    long tenantId();

    /** 敏感字段处理模式：{@link DataExportMode#FULL 完整} / {@link DataExportMode#MASKED 脱敏}。 */
    DataExportMode mode();

    /** 在当前 section 子目录下写入一个二进制条目（{@code path} 不以 {@code /} 开头）。 */
    void put(String path, byte[] content);

    /** UTF-8 文本条目便捷写入（JSON / CSV / TXT 等）。 */
    default void putText(String path, String content) {
        put(path, content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 登记一类数据的条目数，汇总进申请台账的内容概要（如 {@code count("成员", 12)}）。
     * 仅用于展示，不影响打包内容。
     */
    void count(String label, long value);
}
