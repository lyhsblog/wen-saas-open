package com.wensaas.core.web.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 成员管理请求体集合。
 */
public final class MemberRequests {

    private MemberRequests() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 16) String username,
            @NotBlank @Size(max = 64) String realName,
            // 手机号创建时强制必填（O4-1）：消除「空手机号→无法走双通道改手机」死锁。
            @NotBlank(message = "请输入手机号")
            @Pattern(regexp = "1[3-9]\\d{9}", message = "请输入正确的 11 位手机号")
            String mobile,
            String email,
            @NotNull Long mainDeptId,
            List<Long> secondaryDeptIds,
            List<Long> roleIds,
            @Size(max = 80) String positionCode,
            String remark) {
    }

    public record UpdateRequest(
            @Size(max = 64) String realName,
            String mobile,
            String email,
            Integer gender,
            LocalDate birthday,
            @Size(max = 32) String empNo,
            @Size(max = 80) String positionCode,
            List<Long> roleIds) {
    }

    public record RolesRequest(List<Long> roleIds) {
    }

    public record DeptsRequest(@NotNull Long mainDeptId, List<Long> secondaryDeptIds) {
    }
}
