package com.wensaas.core.api.audit;

import java.time.Instant;
import java.util.List;

/**
 * 操作日志事件（{@code sys_operation_log} 一行的契约对照）。已脱敏。
 *
 * <p>由 {@code AuditableAspect} / 业务方主动调 {@code AuditLogRecorder} 构造，
 * Publisher 异步发到 RocketMQ；Consumer 批量写表。
 *
 * @param id              雪花 ID；Publisher 入口为 null 时由其生成
 * @param tenantId        操作方租户；{@code 0} = 运营
 * @param targetTenantId  运营对租户操作时的目标租户 ID
 * @param requestId       同请求多日志聚合
 * @param operatorId      操作人；系统调用时可为 null（一般填 0）
 * @param operatorName    操作人姓名快照
 * @param operatorDeptId  操作人主部门快照（数据权限过滤用）
 * @param module          业务模块编码
 * @param action          create / update / delete / 自定义
 * @param objectType      对象类型名（如"成员"）
 * @param objectId        对象 ID（字符串以兼容非 BIGINT）
 * @param objectDesc      对象描述
 * @param method          HTTP 方法
 * @param path            HTTP 路径
 * @param statusCode      HTTP 状态码；异常路径默认 500
 * @param durationMs      耗时
 * @param errorMessage    异常 message（截断 1024 字符）
 * @param ip              来源 IP
 * @param userAgent       UA
 * @param createdAt       事件发生时刻；分区表必需，不能为 null
 * @param objectRefs      多主对象引用（写入 object_refs）；为空时写入侧从 objectType/id/desc 派生 1 元素
 */
public record AuditEvent(
        Long id,
        Long tenantId,
        Long targetTenantId,
        String requestId,
        Long operatorId,
        String operatorName,
        Long operatorDeptId,
        String module,
        String action,
        String objectType,
        String objectId,
        String objectDesc,
        String method,
        String path,
        Integer statusCode,
        Integer durationMs,
        String errorMessage,
        String ip,
        String userAgent,
        Instant createdAt,
        List<AuditObjectRef> objectRefs) {

    /** 兼容构造：不带 objectRefs（写入侧从主对象派生）。 */
    public AuditEvent(Long id, Long tenantId, Long targetTenantId, String requestId,
                      Long operatorId, String operatorName, Long operatorDeptId,
                      String module, String action, String objectType, String objectId, String objectDesc,
                      String method, String path, Integer statusCode, Integer durationMs, String errorMessage,
                      String ip, String userAgent, Instant createdAt) {
        this(id, tenantId, targetTenantId, requestId, operatorId, operatorName, operatorDeptId,
                module, action, objectType, objectId, objectDesc, method, path, statusCode, durationMs,
                errorMessage, ip, userAgent, createdAt, null);
    }

    /**
     * 返回仅替换 {@code statusCode} 的副本。
     * 用于审计失败路径：切面记录时最终 HTTP 状态未定（异常尚未经 advice 映射），
     * 由响应提交后的 filter 读取真实状态回填。
     */
    public AuditEvent withStatusCode(Integer newStatusCode) {
        return new AuditEvent(id, tenantId, targetTenantId, requestId, operatorId, operatorName,
                operatorDeptId, module, action, objectType, objectId, objectDesc, method, path,
                newStatusCode, durationMs, errorMessage, ip, userAgent, createdAt, objectRefs);
    }
}
