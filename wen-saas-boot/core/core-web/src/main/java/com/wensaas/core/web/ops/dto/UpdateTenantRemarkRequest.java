package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.Size;

public record UpdateTenantRemarkRequest(
        @Size(max = 500) String remark
) {}
