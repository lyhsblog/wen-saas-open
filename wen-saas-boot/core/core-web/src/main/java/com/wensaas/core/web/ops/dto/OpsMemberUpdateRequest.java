package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 运营团队成员 · 编辑请求体（不改 username / password）。 */
public record OpsMemberUpdateRequest(
        @Schema(description = "姓名", nullable = true)
        @Size(max = 64)
        String realName,

        @Schema(description = "邮箱", nullable = true)
        @Email
        String email,

        // 编辑表单回填的是脱敏占位（如 138****2002），未修改时原样提交；
        // 故除合法手机号外，须放行掩码形态（\d{3}\*{4}\d{4}），由服务层 unchangedIfMask 判定为未修改。
        // 邮箱 @Email 天然容忍掩码（a***e@x.com）才得以走到守卫，此处与之对齐。
        @Schema(description = "手机号（或脱敏占位，表示未修改）", nullable = true)
        @Pattern(regexp = "^(1[3-9]\\d{9}|\\d{3}\\*{4}\\d{4})$", message = "手机号格式不正确")
        String mobile,

        @Schema(description = "性别 0/1/2", nullable = true)
        Integer gender,

        @Schema(description = "生日", nullable = true)
        LocalDate birthday,

        @Schema(description = "员工编号", nullable = true)
        @Size(max = 40)
        String empNo,

        @Schema(description = "角色 ID 列表（非 null 时全量替换）", nullable = true)
        List<Long> roleIds
) {}
