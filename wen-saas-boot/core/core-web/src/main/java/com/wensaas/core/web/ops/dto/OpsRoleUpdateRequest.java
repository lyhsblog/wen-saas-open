package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 运营角色 · 编辑请求体（不含权限点 / 成员）。 */
public record OpsRoleUpdateRequest(
        @Schema(description = "角色名", nullable = true)
        @Size(max = 128)
        String name,

        @Schema(description = "描述", nullable = true)
        String description,

        @Schema(description = "数据范围 ALL / OWNED", nullable = true)
        String dataScope
) {}
