package com.wensaas.core.web.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "移动部门到新上级")
public record DeptMoveRequest(
        @Schema(description = "新上级 ID；0 表示移到一级", type = "string", format = "int64")
        @NotNull
        Long newParentId) {
}
