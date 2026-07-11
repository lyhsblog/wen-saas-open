package com.wensaas.core.impl.ops.monitor;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_module_access_log} 表实体（混合表 + 月度分区）。
 *
 * <p>命中业务模块 API 前缀的所有请求（含查询读）埋点，支撑平台监控看板的各模块调用次数 / 月活
 * （O9）。由 {@link com.wensaas.core.impl.ops.monitor.ModuleAccessLogFilter} 在
 * chain.doFilter 后构造，经 {@link ModuleAccessBatchBuffer} 异步批量入库。
 *
 * <p>分区表注意事项：{@code createdAt} 必须在 INSERT 前应用层赋值；不软删；不用 @TableLogic。
 * {@code tenant_id} 是访问者归属（进 platform-only-tables 白名单，多租户拦截器跳过）。
 */
@TableName("sys_module_access_log")
@Getter
@Setter
public class SysModuleAccessLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String moduleCode;
    private Long operatorId;
    private String method;
    private String path;
    private Integer statusCode;
    private Integer durationMs;
    private LocalDateTime createdAt;

}
