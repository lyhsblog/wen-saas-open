package com.wensaas.core.impl.openapi.calllog;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_api_call_log} 表实体（混合表 + 月度分区）。
 *
 * <p>分区表注意事项：{@code createdAt} 必须在 INSERT 前应用层赋值；不软删；不用 @TableLogic。
 */
@TableName(value = "sys_api_call_log", autoResultMap = true)
@Getter
@Setter
public class SysApiCallLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long apiKeyId;
    private String apiKeyName;
    private String requestId;
    private String method;
    private String path;
    private Integer statusCode;
    private Integer durationMs;
    private Integer requestSize;
    private Integer responseSize;
    private String errorMessage;
    private String ip;
    private String userAgent;
    private String moduleCode;
    private LocalDateTime createdAt;

}
