package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 修改运营个人基本信息。
 * 姓名不可自助修改，仅由运营管理员在「运营成员管理」处变更。
 *
 * @param gender       性别 0=未知/1=男/2=女（可空）
 * @param birthday     生日（可空）
 * @param avatarFileId 头像文件 ID（可空；来自 POST /api/v1/files/upload 返回的 fileId）
 */
public record UpdateOpsProfileRequest(
        Integer gender,
        LocalDate birthday,
        @Size(max = 64) String avatarFileId) {
}
