package com.wensaas.core.api.compliance;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 数据隐私与合规总览指标（本切片聚焦超量导出审批）。
 *
 * <p>审批触发由「各导出对象是否需审批」开关驱动（不按数量阈值评估），
 * 故指标含「需审批的导出对象数」而无通用阈值。
 *
 * @param pendingCount          待审批数
 * @param overtimePendingCount  已超时未处理的待审批数（按 policy.timeoutHours 派生）
 * @param approved30d           近 30 天批准数
 * @param rejected30d           近 30 天拒绝数
 * @param approvalRequiredCount 当前需审批的导出对象数
 * @param approvalAuthority     审批权限：MAIN_ADMIN / ROLE_PERMISSION
 * @param timeoutHours          审批超时提醒（小时，null=不提醒）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ComplianceOverview {
    private long pendingCount;
    private long overtimePendingCount;
    private long approved30d;
    private long rejected30d;
    private int approvalRequiredCount;
    private String approvalAuthority;
    private Integer timeoutHours;

}
