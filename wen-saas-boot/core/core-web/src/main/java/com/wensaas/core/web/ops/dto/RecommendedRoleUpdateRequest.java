package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 编辑推荐角色模板请求体。null 字段表示不改。 */
public record RecommendedRoleUpdateRequest(
        @Schema(description = "模板名；null=不改", nullable = true)
        @Size(max = 128)
        String name,

        @Schema(description = "描述；null=不改", nullable = true)
        @Size(max = 1024)
        String description,

        @Schema(description = "默认数据范围（1/2/3）；null=不改", nullable = true)
        @Min(1) @Max(3)
        Integer dataScope,

        @Schema(description = "关联套餐包编码（全量替换）；null=不改；已发布禁止移除已关联项", nullable = true)
        List<String> packageCodes,

        @Schema(description = "权限点编码（全量替换）；null=不改；已发布禁止移除已包含项", nullable = true)
        List<String> permissionCodes
) {}
