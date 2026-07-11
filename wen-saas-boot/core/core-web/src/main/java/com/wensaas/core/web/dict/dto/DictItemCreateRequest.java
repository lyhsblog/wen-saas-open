package com.wensaas.core.web.dict.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

@Schema(description = "新建字典项请求")
public record DictItemCreateRequest(
        @Schema(description = "项编码（同分组内唯一）", example = "PLAN")
        @NotBlank @Size(max = 64)
        String code,

        @Schema(description = "项名称", example = "规划")
        @NotBlank @Size(max = 128)
        String name,

        @Schema(description = "父项 ID；非层级分组或顶级项传 null/0", nullable = true,
                type = "string", format = "int64")
        Long parentId,

        @Schema(description = "排序权重（同级），数字小靠前", example = "10", nullable = true)
        Integer sortOrder,

        @Schema(description = "备注", nullable = true)
        @Size(max = 255)
        String remark,

        @Schema(description = "扩展字段值（按分组 schema 必填项强制）", nullable = true)
        Map<String, Object> extra) {
}
