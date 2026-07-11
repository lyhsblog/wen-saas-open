package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 运营角色 · 成员全量替换请求体。 */
public record OpsRoleMembersRequest(
        @Schema(description = "应在该角色下的用户 ID 全集（替换式）")
        @NotNull
        List<Long> userIds
) {}
