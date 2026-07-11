package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokePlanRequest(
        @NotBlank @Size(max = 500) String reason,
        boolean notifyAdmin
) {}
