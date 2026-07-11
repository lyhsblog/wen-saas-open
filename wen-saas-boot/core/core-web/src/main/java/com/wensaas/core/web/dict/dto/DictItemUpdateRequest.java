package com.wensaas.core.web.dict.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.Map;

@Schema(description = "编辑字典项请求；空字段表示不改")
public record DictItemUpdateRequest(
        @Schema(description = "项名称", nullable = true)
        @Size(max = 128)
        String name,

        @Schema(description = "排序权重", nullable = true)
        Integer sortOrder,

        @Schema(description = "备注", nullable = true)
        @Size(max = 255)
        String remark,

        @Schema(description = "扩展字段值", nullable = true)
        Map<String, Object> extra) {
}
