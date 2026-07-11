package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 运营团队成员 · 新建请求体。 */
public record OpsMemberCreateRequest(
        @Schema(description = "账号（字母开头 4-16 位）", example = "alice")
        @NotBlank
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{3,15}$")
        String username,

        @Schema(description = "初始密码（≥8 位，3 类字符）", example = "Init@2026!")
        @NotBlank
        @Size(min = 8, max = 64)
        String password,

        @Schema(description = "姓名")
        @NotBlank
        @Size(max = 64)
        String realName,

        @Schema(description = "邮箱（运营内部必填）")
        @NotBlank
        @Email
        String email,

        @Schema(description = "手机号")
        @NotBlank
        @Pattern(regexp = "^1[3-9]\\d{9}$")
        String mobile,

        @Schema(description = "角色 ID 列表", nullable = true)
        List<Long> roleIds
) {}
