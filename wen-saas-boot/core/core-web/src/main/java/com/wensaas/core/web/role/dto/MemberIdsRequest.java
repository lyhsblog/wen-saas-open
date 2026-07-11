package com.wensaas.core.web.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "成员 ID 集合")
public record MemberIdsRequest(
        @Schema(description = "用户 ID 列表",
                example = "[\"7234234234234234234\",\"7234234234234234235\"]")
        @NotEmpty @Size(max = 200)
        List<Long> userIds) {
}
