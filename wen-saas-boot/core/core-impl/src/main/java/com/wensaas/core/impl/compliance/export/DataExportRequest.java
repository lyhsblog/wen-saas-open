package com.wensaas.core.impl.compliance.export;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_data_export_request} 全量数据导出申请实体（混合表，tenant_id 隔离）。
 *
 * <p>完成列（概要 / 文件 / 令牌 / 失效 / 完成时间）打包后才写，故用 {@link FieldStrategy#ALWAYS}
 * 让 null→值的 update 也落库。
 */
@TableName("sys_data_export_request")
@Getter
@Setter
public class DataExportRequest {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String requestNo;
    private String reason;
    private String dataMode;
    private String scopeSummary;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String itemSummary;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String fileName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long fileSize;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String downloadToken;
    /** 文件中心 fileId（打包完成后写，下载端点据此换签名 URL）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String fileId;
    /** 关联任务中心 sys_task.id（提交时写，便于前端查打包进度）。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long taskId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant expiresAt;
    private Long applicantId;
    private String applicantName;
    private Instant requestedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant completedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
