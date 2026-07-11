package com.wensaas.core.api.compliance.export;

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
 * 超量导出策略视图。审批触发由「各导出对象是否需审批」开关驱动，不按数量阈值评估。
 *
 * @param objects           各可导出对象的审批开关（业务声明 + 租户覆盖合并）
 * @param approvalAuthority  审批权限：MAIN_ADMIN / ROLE_PERMISSION
 * @param timeoutHours       审批超时提醒（小时，null=不提醒）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class ExportPolicyView {
    private List<ExportObjectRule> objects;
    private String approvalAuthority;
    private Integer timeoutHours;

}
