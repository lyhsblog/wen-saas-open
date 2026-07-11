package com.wensaas.core.api.compliance.retention;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 数据留存与软删除策略总览。
 *
 * <p>面向主管理员的自助透明面：声明式策略矩阵 + 文件回收站实时统计 + 自动清理节奏。
 * 真删（彻底销户）不在产品自助层面提供，走运营线下流程。
 *
 * @param policies          软删除 / 留存策略矩阵（声明式）
 * @param recycleBin        文件回收站实时统计
 * @param fileRetentionDays 文件回收站保留天数（超期硬删，默认 30）
 * @param purgeSchedule     自动清理节奏描述（如「每天 05:00」）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class RetentionOverview {
    private List<RetentionPolicyItem> policies;
    private FileRecycleBinStats recycleBin;
    private int fileRetentionDays;
    private String purgeSchedule;

}
