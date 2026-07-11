package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 复制推荐角色模板请求体：以源模板为初值，重填名称，产出草稿。 */
public record RecommendedRoleCopyRequest(
        @Schema(description = "新模板名（运营内部唯一）", example = "销售员（副本）")
        @NotBlank
        @Size(max = 128)
        String name
) {}
