package com.wensaas.core.impl.compliance.export;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_export_approval_rule} 导出对象审批开关覆盖（混合表，tenant_id 隔离）。
 *
 * <p>每租户每导出对象至多一行；缺失时回退业务方声明的 {@code defaultRequiresApproval}。
 */
@TableName("sys_export_approval_rule")
@Getter
@Setter
public class ExportApprovalRule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String objectCode;
    private Boolean requiresApproval;
    private LocalDateTime updatedAt;
    private Long updatedBy;

}
