package com.wensaas.core.web.ops.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 运营开通新租户请求体。
 */
public record CreateTenantRequest(

        @NotBlank
        @Size(max = 64)
        String tenantName,

        // 租户标识会作为子域名 label，须合法 DNS label（与 TenantCodePolicy 同口径）：
        // 小写字母/数字/连字符、首尾非连字符、长度 5-16；5-16 下限天然规避平台端短子域名前缀。
        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{3,14}[a-z0-9]$",
                message = "子域名须小写字母或数字开头，由小写字母、数字、连字符组成，不以连字符结尾，长度 5-16 位")
        String tenantCode,

        @Size(max = 500)
        String tenantRemark,

        @NotBlank
        @Size(min = 3, max = 32, message = "账号长度 3-32 位")
        @Pattern(regexp = "[a-zA-Z0-9_\\-\\.]+", message = "账号只允许字母、数字、下划线、连字符、点")
        String adminUsername,

        @NotBlank(message = "请输入主管理员姓名")
        @Size(max = 64)
        String adminRealName,

        // 主管理员手机号创建时强制必填（O4-1）：消除「空手机号→无法走双通道改手机」死锁。
        @NotBlank(message = "请输入主管理员手机号")
        @Pattern(regexp = "1[3-9]\\d{9}", message = "请输入有效的 11 位手机号")
        String adminMobile,

        // 邮箱必填：开通时激活链接会发到主管理员邮箱，且邮箱是登录/找回通道之一。
        @NotBlank(message = "请输入主管理员邮箱")
        @Email
        @Size(max = 128)
        String adminEmail,

        /** 客户经理（运营账号 id，非必填）。 */
        Long csmUserId,

        /** 成员数上限（null=不限制）。 */
        @PositiveOrZero
        Integer memberCap,

        /** 存储上限字节数（null=不限制）。 */
        @PositiveOrZero
        Long storageCap,

        /** 租户级有效期（null=永久）。 */
        Instant expireAt,

        /** 租户时区 IANA 标识（null=平台默认）。 */
        @Size(max = 64)
        String timezone,

        /** 公司全称。 */
        @Size(max = 128)
        String fullName,

        /** 统一社会信用代码。 */
        @Size(max = 32)
        String uscc,

        /** 行业。 */
        @Size(max = 64)
        String industry,

        /** 规模。 */
        @Size(max = 32)
        String scale,

        /** 所在地区。 */
        @Size(max = 128)
        String region,

        /** 标签。 */
        List<String> tags,

        /** 开通时加购席位数（可选，null/缺省=0 表示未加购）。 */
        @PositiveOrZero
        Integer seatAddonQty,

        @Size(max = 200)
        String seatAddonRemark,

        List<String> packageCodes
) {}
