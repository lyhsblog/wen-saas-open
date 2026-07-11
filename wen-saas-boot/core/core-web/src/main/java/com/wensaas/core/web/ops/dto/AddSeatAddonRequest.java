package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record AddSeatAddonRequest(
        @Min(1) int quantity,
        @Size(max = 500) String remark
) {}
