package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignPlanRequest(
        @NotBlank String packageCode,
        Integer ttlDays,
        @Size(max = 500) String remark
) {}
