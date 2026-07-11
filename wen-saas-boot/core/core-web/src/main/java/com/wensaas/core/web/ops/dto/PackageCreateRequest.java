package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 新建套餐请求体（OPS-PKG-002）。 */
public record PackageCreateRequest(
        @Schema(description = "套餐编码（全局唯一，^[A-Z][A-Z0-9_]{1,63}$，创建后不可改）", example = "CRM_STD")
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String code,

        @Schema(description = "套餐名", example = "CRM 标准包")
        @NotBlank
        @Size(max = 128)
        String name,

        @Schema(description = "描述", nullable = true)
        @Size(max = 1024)
        String description,

        @Schema(description = "默认有效期天数；null=永久", nullable = true, example = "365")
        @Positive
        Integer defaultTtlDays,

        @Schema(description = "勾选的业务模块编码列表；可空", nullable = true)
        List<String> moduleCodes,

        @Schema(description = "true=直接上架；false / 缺省=保存为草稿", example = "false", nullable = true)
        Boolean publishImmediately
) {
    /** null 安全：未传或 null 视为保存草稿（入站 @RequestBody 不用 primitive boolean，缺省会 400）。 */
    public boolean publishNow() {
        return Boolean.TRUE.equals(publishImmediately);
    }
}
