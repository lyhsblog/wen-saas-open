package com.wensaas.core.web.dict.dto;

import com.wensaas.core.api.spi.def.DictExtraFieldDef;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "新建字典分组请求")
public record DictGroupCreateRequest(
        @Schema(description = "分组编码，租户内唯一；创建后不可改", example = "PROJECT_STAGE")
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$",
                message = "code 必须大写字母开头，仅含大写字母 / 数字 / 下划线")
        String code,

        @Schema(description = "分组名称", example = "项目阶段")
        @NotBlank @Size(max = 64)
        String name,

        @Schema(description = "描述", nullable = true)
        @Size(max = 255)
        String description,

        @Schema(description = "是否支持层级（创建后不可改）", example = "false")
        boolean supportHierarchy,

        @Schema(description = "扩展字段 schema，无扩展时传空数组", nullable = true)
        List<DictExtraFieldDef> extraSchema) {
}
