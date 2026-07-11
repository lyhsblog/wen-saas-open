package com.wensaas.core.impl.file;

/**
 * {@code sys_file} 回收站聚合查询结果（见 {@link SysFileMapper#selectRecycleBinAgg}）。
 * 列名 {@code file_count / total_bytes / expiring_soon_count / overdue_count} 经
 * mapUnderscoreToCamelCase 映射。
 */
public class RecycleBinAgg {

    private long fileCount;
    private long totalBytes;
    private long expiringSoonCount;
    private long overdueCount;

    public long getFileCount() { return fileCount; }

    public void setFileCount(long fileCount) { this.fileCount = fileCount; }

    public long getTotalBytes() { return totalBytes; }

    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public long getExpiringSoonCount() { return expiringSoonCount; }

    public void setExpiringSoonCount(long expiringSoonCount) { this.expiringSoonCount = expiringSoonCount; }

    public long getOverdueCount() { return overdueCount; }

    public void setOverdueCount(long overdueCount) { this.overdueCount = overdueCount; }
}
