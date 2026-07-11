package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 编辑套餐请求体（OPS-PKG-004）。null 字段表示不改。 */
public record PackageUpdateRequest(
        @Schema(description = "套餐名；null=不改", nullable = true)
        @Size(max = 128)
        String name,

        @Schema(description = "描述；null=不改", nullable = true)
        @Size(max = 1024)
        String description,

        @Schema(description = "默认有效期天数；null=不改；基础包不可改此字段", nullable = true)
        @Positive
        Integer defaultTtlDays,

        @Schema(description = "勾选的业务模块编码（全量替换）；null=不改；基础包不可改此字段；上架后禁止移除已包含项", nullable = true)
        List<String> moduleCodes
) {}
