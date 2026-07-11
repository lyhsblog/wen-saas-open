package com.wensaas.core.web.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "编辑部门基本信息；空字段不改")
public record DeptUpdateRequest(
        @Schema(description = "部门名", nullable = true)
        @Size(max = 64)
        String name,

        @Schema(description = "排序权重", nullable = true)
        Integer sortOrder) {
}
