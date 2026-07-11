package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 重新指定主管理员请求体。
 *
 * @param mode             "existing" | "new"
 * @param userId           mode=existing 时必填（现有成员 ID）
 * @param username         mode=new 时必填
 * @param realName         mode=new 时可选
 * @param mobile           mode=new 时必填
 * @param email            mode=new 时必填
 * @param oldAdminHandling "demote_admin" | "demote_member" | "disable"
 */
public record TransferMainAdminRequest(
        @NotBlank @Pattern(regexp = "existing|new") String mode,
        String userId,
        @Size(min = 3, max = 32) @Pattern(regexp = "[a-zA-Z0-9_\\-\\.]+") String username,
        @Size(max = 64) String realName,
        @Size(max = 32) String mobile,
        @Size(max = 128) String email,
        @NotBlank @Pattern(regexp = "demote_admin|demote_member|disable") String oldAdminHandling
) {}
