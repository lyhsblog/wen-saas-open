package com.wensaas.core.impl.compliance.export;

import com.wensaas.core.api.compliance.export.DataExportMode;
import com.wensaas.core.api.compliance.spi.DataExportSink;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link DataExportSink} 默认实现：把各 {@link com.wensaas.core.api.compliance.spi.DataExportContributor}
 * 写入的条目按 {@code section/path} 缓存到内存有序表，供 {@link DataExportTaskHandler} 统一打 zip；
 * 同时累计内容概要计数。非线程安全（任务在单消费线程顺序执行）。
 */
final class DataExportCollector implements DataExportSink {

    private final long tenantId;
    private final DataExportMode mode;
    /** zip 条目：相对路径（含 section 前缀）→ 内容。LinkedHashMap 保插入顺序。 */
    private final Map<String, byte[]> entries = new LinkedHashMap<>();
    /** 概要计数：标签 → 条目数。 */
    private final Map<String, Long> counts = new LinkedHashMap<>();
    /** 当前 contributor 的 section 前缀（不为空时自动拼到 put 的 path 前）。 */
    private String section = "";

    DataExportCollector(long tenantId, DataExportMode mode) {
        this.tenantId = tenantId;
        this.mode = mode;
    }

    /** 切到下一个 contributor 的 section 子目录。null / 空表示写到包根。 */
    void section(String section) {
        this.section = (section == null || section.isBlank()) ? "" : section.trim();
    }

    @Override
    public long tenantId() {
        return tenantId;
    }

    @Override
    public DataExportMode mode() {
        return mode;
    }

    @Override
    public void put(String path, byte[] content) {
        String clean = path == null ? "" : path.replaceFirst("^/+", "");
        String full = section.isEmpty() ? clean : section + "/" + clean;
        entries.put(full, content == null ? new byte[0] : content);
    }

    @Override
    public void count(String label, long value) {
        if (label != null && !label.isBlank()) {
            counts.merge(label, value, Long::sum);
        }
    }

    Map<String, byte[]> entries() {
        return entries;
    }

    Map<String, Long> counts() {
        return counts;
    }
}
