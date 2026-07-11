package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.Size;

/** 暂停租户请求体（动词端点 {@code POST /{tenantId}/suspend}）。reason 选填，写入操作日志 object_desc。 */
public record TenantSuspendRequest(
        @Size(max = 500)
        String reason
) {}
