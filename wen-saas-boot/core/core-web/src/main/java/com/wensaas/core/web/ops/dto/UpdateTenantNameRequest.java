package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantNameRequest(
        @NotBlank @Size(max = 64) String newName,
        @NotBlank @Size(max = 500) String reason
) {}
