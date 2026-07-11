package com.wensaas.core.api.tenant;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * 租户单个套餐授权（{@code sys_tenant.plans} jsonb 数组元素）。
 *
 * <p>schema（详见表注释）：
 * <pre>
 * {
 *   "packageCode": "base",
 *   "grantedAt": "2026-01-01T00:00:00Z",
 *   "expiresAt": null,           // null=永久；ISO-8601 = 过期时间
 *   "revokedAt": null            // null=未撤销；ISO-8601 = 撤销时间
 * }
 * </pre>
 *
 * <p>所有时间字段统一用 ISO-8601 字符串（Jackson 默认不带 jsr310 模块，
 * 避免在 entity 层装配自定义 ObjectMapper；判断时按需 parse）。
 *
 * <p>判定 active：{@code revokedAt == null && (expiresAt == null || expiresAt > now)}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantPlan(String packageCode, String grantedAt, String expiresAt, String revokedAt) {

    public boolean isActive(Instant now) {
        if (revokedAt != null) {
            return false;
        }
        return expiresAt == null || parseInstantOrNull(expiresAt, now).isAfter(now);
    }

    /**
     * 是否仅因"过期"导致非 active（用于 ModuleView.readonly 触发判定 —— 见产品 02 三·套餐到期）。
     * 撤销不视为"只读降级"，撤销直接对租户不可见。
     */
    public boolean isExpiredOnly(Instant now) {
        if (revokedAt != null || expiresAt == null) {
            return false;
        }
        Instant exp = parseInstantOrNull(expiresAt, now);
        return !exp.isAfter(now);
    }

    /** parse 失败时回退到 now（保守视为"刚刚过期"），不抛错以免错误的 jsonb 把整租户 capabilities 拖崩。 */
    private static Instant parseInstantOrNull(String iso, Instant fallback) {
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
