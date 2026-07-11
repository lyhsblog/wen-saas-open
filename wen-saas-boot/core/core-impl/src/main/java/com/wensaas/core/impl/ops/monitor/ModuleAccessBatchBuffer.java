package com.wensaas.core.impl.ops.monitor;

import com.wensaas.core.impl.audit.pipeline.AuditBatchBuffer;

import java.time.Duration;
import java.util.List;

/**
 * 业务模块访问埋点批量缓冲（复用 {@link AuditBatchBuffer}：100 条 / 5 秒窗口，内存队列后台 flush）。
 *
 * <p>与 openapi 调用日志不同，模块访问埋点不经 RocketMQ —— Filter 直接 offer，本 buffer 批量
 * INSERT 到分区表 {@code sys_module_access_log}（单体内存缓冲足够，避免引入额外 MQ topic）。
 */
public class ModuleAccessBatchBuffer extends AuditBatchBuffer<SysModuleAccessLog> {

    private final SysModuleAccessLogMapper mapper;

    public ModuleAccessBatchBuffer(SysModuleAccessLogMapper mapper) {
        super("module-access", 100, Duration.ofSeconds(5));
        this.mapper = mapper;
    }

    @Override
    protected void flush(List<SysModuleAccessLog> batch) {
        mapper.insertBatch(batch);
    }
}
