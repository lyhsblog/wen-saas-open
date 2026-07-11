package com.wensaas.core.web.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "编辑角色请求；空字段不改")
public record RoleUpdateRequest(
        @Schema(description = "角色名称", nullable = true)
        @Size(max = 64)
        String name,

        @Schema(description = "描述", nullable = true)
        @Size(max = 255)
        String description,

        @Schema(description = "数据范围",
                allowableValues = {"ALL", "DEPT_AND_BELOW", "DEPT_ONLY", "SELF"},
                nullable = true)
        String dataScope) {
}
