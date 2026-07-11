package com.wensaas.core.impl.openapi.calllog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开放 API 调用日志查询（租户端「调用日志」tab 的读路径）。
 *
 * <p>租户隔离由租户线拦截器自动叠加（{@code sys_api_call_log} 是混合表）；传其他租户的
 * {@code apiKeyId} 只会得到空页。表按 {@code created_at} 月度分区，带时间窗过滤可命中
 * 分区裁剪；默认倒序走 {@code idx_apilog_tenant_time} / {@code idx_apilog_apikey}。
 *
 * <p>只读服务：日志写入在 {@link ApiCallBatchBuffer}（事件缓冲批量落库），本类不做写。
 */
public class ApiCallLogQueryService {

    private final SysApiCallLogMapper mapper;

    public ApiCallLogQueryService(SysApiCallLogMapper mapper) {
        this.mapper = mapper;
    }

    public LogPage page(Long apiKeyId, Integer statusCode, LocalDateTime from, LocalDateTime to,
                        long pageNo, long size) {
        LambdaQueryWrapper<SysApiCallLog> w = new LambdaQueryWrapper<SysApiCallLog>()
                .eq(apiKeyId != null, SysApiCallLog::getApiKeyId, apiKeyId)
                .eq(statusCode != null, SysApiCallLog::getStatusCode, statusCode)
                .ge(from != null, SysApiCallLog::getCreatedAt, from)
                .lt(to != null, SysApiCallLog::getCreatedAt, to)
                .orderByDesc(SysApiCallLog::getCreatedAt);
        Page<SysApiCallLog> p = mapper.selectPage(new Page<>(pageNo, size), w);
        List<LogItem> items = p.getRecords().stream()
                .map(r -> new LogItem(r.getId(), r.getApiKeyId(), r.getRequestId(), r.getMethod(),
                        r.getPath(), r.getStatusCode(), r.getDurationMs(), r.getErrorMessage(),
                        r.getIp(), r.getModuleCode(), r.getCreatedAt()))
                .toList();
        return new LogPage(items, p.getTotal());
    }

    // ============================== DTO ==============================

    /** 日志行（{@code apiKeyName} 行内为空不冗余，前端按凭证列表映射名称）。 */
    public record LogItem(Long id, Long apiKeyId, String requestId, String method, String path,
                          Integer statusCode, Integer durationMs, String errorMessage,
                          String ip, String moduleCode, LocalDateTime createdAt) {
    }

    public record LogPage(List<LogItem> records, long total) {
    }
}
