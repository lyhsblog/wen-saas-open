package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 新建推荐角色模板请求体。 */
public record RecommendedRoleCreateRequest(
        @Schema(description = "模板名（运营内部唯一）", example = "销售员")
        @NotBlank
        @Size(max = 128)
        String name,

        @Schema(description = "描述（给租户看的角色描述）", nullable = true)
        @Size(max = 1024)
        String description,

        @Schema(description = "默认数据范围：1=全部 / 2=本部门含下属 / 3=仅本人", example = "3")
        @Min(1) @Max(3)
        int dataScope,

        @Schema(description = "关联套餐包编码（≥ 1 个；任一被租户勾选即触发推荐）")
        @NotEmpty
        List<String> packageCodes,

        @Schema(description = "包含的权限点编码（跨业务模块，非 ops.*）；可空", nullable = true)
        List<String> permissionCodes,

        @Schema(description = "true=直接发布；false / 缺省=保存为草稿", example = "false", nullable = true)
        Boolean publishImmediately
) {
    /** null 安全：未传或 null 视为保存草稿（入站 @RequestBody 不用 primitive boolean，缺省会 400）。 */
    public boolean publishNow() {
        return Boolean.TRUE.equals(publishImmediately);
    }
}
