package com.wensaas.core.impl.openapi.calllog;

import java.time.Instant;

/**
 * 开放 API 调用事件，由 {@link ApiCallLogFilter} 在 chain.doFilter 结束后构造并异步发布。
 *
 * @param id           应用层雪花 ID（复合主键 (id, created_at) 中的 id）
 * @param tenantId     租户；匿名 / 鉴权失败请求填 0
 * @param apiKeyId     凭证 ID；鉴权失败 / 公开端点为 null
 * @param apiKeyName   凭证名快照（凭证可能撤销）
 * @param requestId    请求链路 ID（RequestContext.get()）
 * @param method       HTTP method
 * @param path         请求路径
 * @param statusCode   响应状态码
 * @param durationMs   chain.doFilter 全程耗时
 * @param requestSize  请求体大小（字节）；不能直接取时填 null
 * @param responseSize 响应体大小（字节）；不能直接取时填 null
 * @param errorMessage 异常信息（截断 1024 字符）
 * @param ip           客户端 IP
 * @param userAgent    User-Agent
 * @param moduleCode   业务模块编码（ModuleApiRouteRegistry 解析；命中不到为 null）
 * @param createdAt    时间戳；调用方主动赋值（分区表必需）
 */
public record ApiCallEvent(Long id, Long tenantId, Long apiKeyId, String apiKeyName,
                            String requestId, String method, String path,
                            Integer statusCode, Integer durationMs,
                            Integer requestSize, Integer responseSize,
                            String errorMessage, String ip, String userAgent,
                            String moduleCode,
                            Instant createdAt) {
}
