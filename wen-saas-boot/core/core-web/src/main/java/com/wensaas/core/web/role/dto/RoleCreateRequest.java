package com.wensaas.core.web.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "新建角色请求")
public record RoleCreateRequest(
        @Schema(description = "角色编码（租户内唯一，创建后不可改）", example = "FINANCE_OPS")
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,63}$",
                message = "code 必须字母开头，仅含字母 / 数字 / 下划线")
        String code,

        @Schema(description = "角色名称", example = "财务专员")
        @NotBlank @Size(max = 64)
        String name,

        @Schema(description = "描述", nullable = true)
        @Size(max = 255)
        String description,

        @Schema(description = "数据范围：ALL / DEPT_AND_BELOW / DEPT_ONLY / SELF",
                allowableValues = {"ALL", "DEPT_AND_BELOW", "DEPT_ONLY", "SELF"},
                example = "DEPT_AND_BELOW")
        @NotBlank
        String dataScope) {
}
