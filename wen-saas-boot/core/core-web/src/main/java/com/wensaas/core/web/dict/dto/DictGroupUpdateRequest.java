package com.wensaas.core.web.dict.dto;

import com.wensaas.core.api.spi.def.DictExtraFieldDef;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "编辑字典分组请求；空字段表示不改")
public record DictGroupUpdateRequest(
        @Schema(description = "分组名称", nullable = true)
        @Size(max = 64)
        String name,

        @Schema(description = "描述", nullable = true)
        @Size(max = 255)
        String description,

        @Schema(description = "扩展字段 schema（仅 CUSTOM 分组允许）", nullable = true)
        List<DictExtraFieldDef> extraSchema) {
}
