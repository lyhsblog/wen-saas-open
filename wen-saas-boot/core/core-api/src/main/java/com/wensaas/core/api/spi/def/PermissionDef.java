package com.wensaas.core.api.spi.def;

/**
 * 权限点贡献项。
 *
 * @param code 权限点编码，按 {@code {module}.{menu}.{action}} 命名（如 "crm.customer.list"），全局唯一
 * @param name 显示名
 * @param description 描述
 */
public record PermissionDef(String code, String name, String description) {

    public static PermissionDef of(String code, String name) {
        return new PermissionDef(code, name, null);
    }
}
