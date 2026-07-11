package com.wensaas.core.web.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "新建部门请求")
public record DeptCreateRequest(
        @Schema(description = "上级部门 ID；传 0 或 null 表示一级部门",
                nullable = true, type = "string", format = "int64")
        Long parentId,

        @Schema(description = "部门名（同级下唯一）", example = "研发部")
        @NotBlank @Size(max = 64)
        String name,

        @Schema(description = "同级排序权重（数字小靠前）", nullable = true)
        Integer sortOrder) {
}
