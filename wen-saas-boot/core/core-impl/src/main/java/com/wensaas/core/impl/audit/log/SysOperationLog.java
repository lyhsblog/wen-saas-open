package com.wensaas.core.impl.audit.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_operation_log} 表实体（混合表 + 月度分区）。
 *
 * <p>分区表注意事项：
 * <ul>
 *   <li>主键 (id, created_at) 复合；MP {@link TableId} 仅作用 {@code id} 列。</li>
 *   <li>{@code created_at} 必须由应用层在 INSERT 前主动赋值，否则 PG 找不到分区抛
 *       {@code no partition of relation "sys_operation_log" found for row}。</li>
 *   <li>不软删；不用 {@code @TableLogic}。</li>
 * </ul>
 */
@TableName(value = "sys_operation_log", autoResultMap = true)
@Getter
@Setter
public class SysOperationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long targetTenantId;
    private String requestId;
    private Long operatorId;
    private String operatorName;
    private Long operatorDeptId;
    private String module;
    private String action;
    private String objectType;
    private String objectId;
    private String objectDesc;
    /** 多主对象引用 jsonb 文本 [{type,id,name}]；写入侧序列化，读取 jsonb→String（getString）。 */
    private String objectRefs;
    private String method;
    private String path;
    private Integer statusCode;
    private Integer durationMs;
    private String errorMessage;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;

}
