package com.wensaas.core.web.publicapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 公开租户信息（仅 5 个公开字段，不暴露 mainAdminUserId / plans / 内部统计）。
 *
 * @param id            租户 ID（雪花，JSON 序列化为 string）
 * @param code          租户当前编码（前端 tenantKey）
 * @param canonicalCode 租户当前规范编码；与请求 code 不同 = 请求的是旧路由位别名，前端应重定向
 * @param name          租户名
 * @param logoUrl       租户 Logo URL，可空
 * @param status        状态：active / paused / expired
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class TenantPublicView {
    @Schema(description = "租户 ID（雪花 ID，JSON 序列化为 string）", example = "9001", type = "string")
    private String id;
    @Schema(description = "租户编码（URL 路由位）", example = "acme")
    private String code;
    @Schema(description = "租户当前规范编码；与请求 code 不同 = 请求的是旧路由位别名，前端应重定向到此",
                example = "acme")
    private String canonicalCode;
    @Schema(description = "租户名", example = "Acme 公司")
    private String name;
    @Schema(description = "Logo URL", nullable = true, example = "https://cdn.example.com/logo.png")
    private String logoUrl;
    @Schema(description = "状态", allowableValues = {"active", "paused", "expired"})
    private String status;

}
