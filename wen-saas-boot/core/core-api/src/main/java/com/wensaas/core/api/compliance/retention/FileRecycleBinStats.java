package com.wensaas.core.api.compliance.retention;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 文件回收站实时统计（文件软删进回收站，超 30 天自动彻底清理）。
 *
 * <p>口径为当前租户 {@code sys_file} 中 {@code deleted_at IS NOT NULL} 的记录。计数为
 * {@code long}，JSON 序列化为字符串，前端 {@code Number()} 归一。
 *
 * @param fileCount          回收站内文件总数
 * @param totalBytes         回收站内文件总字节数
 * @param expiringSoonCount  即将清理数（删除时间落在 {@code expiringWindowDays} 内到期）
 * @param overdueCount       已超 30 天、待下次清理任务彻底删除数
 * @param expiringWindowDays 「即将清理」预警窗口天数
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class FileRecycleBinStats {
    private long fileCount;
    private long totalBytes;
    private long expiringSoonCount;
    private long overdueCount;
    private int expiringWindowDays;

    public static FileRecycleBinStats empty(int expiringWindowDays) {
        return new FileRecycleBinStats(0, 0, 0, 0, expiringWindowDays);
    }
}
