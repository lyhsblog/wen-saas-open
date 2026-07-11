package com.wensaas.core.web.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "同级批量重排：orderedIds 为该父级下全部子部门按新顺序的 id")
public record DeptReorderRequest(
        @Schema(description = "父级部门 ID；0 或 null 表示一级", type = "string", format = "int64",
                nullable = true)
        Long parentId,

        @Schema(description = "该父级下全部子部门 id，按新顺序排列", type = "array")
        @NotEmpty
        List<Long> orderedIds) {
}
