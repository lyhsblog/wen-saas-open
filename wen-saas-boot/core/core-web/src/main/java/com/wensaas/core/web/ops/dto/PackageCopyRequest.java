package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 复制套餐请求体（OPS-P-013）：以源套餐为模板，重填编码与名称，产出草稿包。 */
public record PackageCopyRequest(
        @Schema(description = "新套餐编码（全局唯一，^[A-Z][A-Z0-9_]{1,63}$）", example = "CRM_STD_COPY")
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String code,

        @Schema(description = "新套餐名", example = "CRM 标准包（副本）")
        @NotBlank
        @Size(max = 128)
        String name
) {}
