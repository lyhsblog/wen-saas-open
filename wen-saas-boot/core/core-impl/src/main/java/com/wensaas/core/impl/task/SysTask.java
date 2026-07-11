package com.wensaas.core.impl.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_task} 表实体（混合表）。
 *
 * <p>单列主键 id（不分区，预期 30 天数据量小）；不软删；过期由 {@code ExpiredTaskCleanerJob} 物理清理。
 */
@TableName(value = "sys_task", autoResultMap = true)
@Getter
@Setter
public class SysTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String taskType;
    private String taskName;
    private String handlerCode;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Map<String, Object> taskParams;
    private String status;
    private Integer progress;
    private Integer totalCount;
    private Integer processedCount;
    private Integer successCount;
    private Integer failedCount;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Map<String, Object> resultData;
    private String errorMessage;
    private Long creatorId;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime expiresAt;

}
