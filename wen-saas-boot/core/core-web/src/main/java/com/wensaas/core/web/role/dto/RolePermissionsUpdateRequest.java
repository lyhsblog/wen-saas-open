package com.wensaas.core.web.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "全量替换角色权限点")
public record RolePermissionsUpdateRequest(
        @Schema(description = "权限点 code 列表（可空数组 = 清空所有权限）",
                example = "[\"crm.customer.list\",\"crm.customer.create\"]")
        @NotNull
        @Size(max = 1000)
        List<String> codes) {
}
