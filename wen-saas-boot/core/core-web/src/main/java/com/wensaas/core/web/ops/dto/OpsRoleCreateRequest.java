package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 运营自定义角色 · 新建请求体。 */
public record OpsRoleCreateRequest(
        @Schema(description = "角色编码（运营域内唯一，^[A-Z][A-Z0-9_]{1,63}$）", example = "OPS_FINANCE")
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String code,

        @Schema(description = "角色名")
        @NotBlank
        @Size(max = 128)
        String name,

        @Schema(description = "描述", nullable = true)
        String description,

        @Schema(description = "数据范围 ALL / OWNED", example = "ALL")
        @NotBlank
        String dataScope
) {}
