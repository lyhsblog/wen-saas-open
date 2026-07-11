package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record RenewPlanRequest(
        @Min(1) int ttlDays,
        @Size(max = 500) String remark
) {}
