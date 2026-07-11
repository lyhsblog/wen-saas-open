package com.wensaas.core.impl.compliance.export;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_export_policy} 超量导出策略实体（每租户单行，混合表）。
 */
@TableName("sys_export_policy")
@Getter
@Setter
public class ExportPolicy {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String approvalAuthority;
    private Integer timeoutHours;
    private LocalDateTime updatedAt;
    private Long updatedBy;

}
