package com.wensaas.core.impl.compliance.export;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_export_request} 超量导出审批台账实体（混合表，tenant_id 隔离）。
 *
 * <p>{@code status} 以字符串存（PENDING/APPROVED/REJECTED），Service 层映射
 * {@link com.wensaas.core.api.compliance.export.ExportRequestStatus}。决策列（审批人 / 意见 / 时间）
 * 决策后才写，故用 {@link FieldStrategy#ALWAYS} 让 null→值的 update 也落库。
 */
@TableName(value = "sys_export_request", autoResultMap = true)
@Getter
@Setter
public class ExportRequest {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String requestNo;
    private String objectType;
    private String objectCode;
    private String filterSummary;
    private Integer estimatedCount;
    private String reason;
    private String status;
    private Long applicantId;
    private String applicantName;
    private String applicantDeptName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long approverId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String approverName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String approvalComment;
    private Instant appliedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant decidedAt;
    /** 导出执行参数快照：{@code {"filters":{...},"operator":{userId,username,tenantOwner,mainDeptId}}}。 */
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Map<String, Object> executeParams;
    /** 审批通过后投递的任务中心任务 ID（批准前为 null）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long taskId;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
