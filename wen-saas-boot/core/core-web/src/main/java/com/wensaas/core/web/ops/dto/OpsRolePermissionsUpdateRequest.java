package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 运营角色 · 权限点全量替换请求体。 */
public record OpsRolePermissionsUpdateRequest(
        @Schema(description = "ops.* 权限点 code 全集（替换式）")
        @NotNull
        List<String> permissionCodes
) {}
