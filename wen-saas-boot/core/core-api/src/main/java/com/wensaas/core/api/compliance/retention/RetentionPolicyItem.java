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
 * 软删除 / 数据留存策略矩阵的一行（声明式，产品既定策略）。
 *
 * @param objectType     对象类型，如「成员账号」「文件」「操作日志」
 * @param deleteBehavior 删除行为说明
 * @param hardDelete     是否涉及物理硬删（true=会彻底清理，false=仅软删 / 停用 / 永久保留）
 * @param note           合规备注（可空）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class RetentionPolicyItem {
    private String objectType;
    private String deleteBehavior;
    private boolean hardDelete;
    private String note;

}
