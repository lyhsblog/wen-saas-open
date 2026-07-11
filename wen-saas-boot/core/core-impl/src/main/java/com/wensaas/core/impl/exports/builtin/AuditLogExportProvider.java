package com.wensaas.core.impl.exports.builtin;

import com.wensaas.core.api.exports.spi.ExportColumn;
import com.wensaas.core.api.exports.spi.ExportDataProvider;
import com.wensaas.core.api.exports.spi.ExportSink;
import com.wensaas.core.api.tenant.audit.TenantOperationLogQueryService;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 审计日志导出（导出对象 {@code audit_log}，与 {@code BaseExportObjectContributor} 声明对齐，
 * 默认需审批）。复用 {@link TenantOperationLogQueryService}（UX3 审计日志视图同源），
 * {@code restrictOperatorId=null}——发起权限 {@code audit.view} 与页面一致（管理员看全租户）。
 *
 * <p>filters：{@code startAt} / {@code endAt}（ISO-8601 Instant）/ {@code operatorId} /
 * {@code action} / {@code objectType} / {@code requestId}（与审计日志页筛选一致）。
 */
public class AuditLogExportProvider implements ExportDataProvider {

    private static final int PAGE_SIZE = 500;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TenantOperationLogQueryService queryService;

    public AuditLogExportProvider(TenantOperationLogQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public String objectCode() {
        return "audit_log";
    }

    @Override
    public String requiredPermission() {
        return "audit.view";
    }

    @Override
    public List<ExportColumn> columns() {
        return List.of(
                new ExportColumn("createdAt", "时间"),
                new ExportColumn("operatorName", "操作人"),
                new ExportColumn("module", "模块"),
                new ExportColumn("action", "操作"),
                new ExportColumn("objectType", "对象类型"),
                new ExportColumn("objectDesc", "操作对象"),
                new ExportColumn("method", "请求方法"),
                new ExportColumn("path", "请求路径"),
                new ExportColumn("statusCode", "状态码"),
                new ExportColumn("durationMs", "耗时(ms)"),
                new ExportColumn("ip", "IP"),
                new ExportColumn("requestId", "RequestID"));
    }

    @Override
    public long estimateCount(Map<String, String> filters) {
        return queryService.list(query(filters, 1, 1), null).total();
    }

    @Override
    public void export(Map<String, String> filters, ExportSink sink) {
        int page = 1;
        while (!sink.stopped()) {
            TenantOperationLogQueryService.OpLogPage result =
                    queryService.list(query(filters, page, PAGE_SIZE), null);
            for (TenantOperationLogQueryService.OpLogListItem r : result.records()) {
                sink.row(Arrays.asList(
                        r.createdAt() == null ? null : TIME.format(r.createdAt()),
                        r.operatorName(),
                        r.module(),
                        r.action(),
                        r.objectType(),
                        r.objectDesc(),
                        r.method(),
                        r.path(),
                        r.statusCode() == null ? null : String.valueOf(r.statusCode()),
                        r.durationMs() == null ? null : String.valueOf(r.durationMs()),
                        r.ip(),
                        r.requestId()));
            }
            if (result.records().size() < PAGE_SIZE) {
                return;
            }
            page++;
        }
    }

    private static TenantOperationLogQueryService.OpLogQuery query(Map<String, String> f,
                                                                   int page, int size) {
        return new TenantOperationLogQueryService.OpLogQuery(
                page, size,
                parseInstant(f.get("startAt")),
                parseInstant(f.get("endAt")),
                parseLong(f.get("operatorId")),
                blankToNull(f.get("action")),
                blankToNull(f.get("objectType")),
                blankToNull(f.get("requestId")),
                blankToNull(f.get("objectId")));
    }

    private static Instant parseInstant(String s) {
        return s == null || s.isBlank() ? null : Instant.parse(s);
    }

    private static Long parseLong(String s) {
        return s == null || s.isBlank() ? null : Long.valueOf(s);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
