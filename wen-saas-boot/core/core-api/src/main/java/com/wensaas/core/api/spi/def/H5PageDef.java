package com.wensaas.core.api.spi.def;

/**
 * H5 页面贡献项。
 *
 * @param route H5 路由，按 {@code {module}/{path}} 命名（如 "crm/customers"），全局唯一
 * @param name 显示名
 */
public record H5PageDef(String route, String name) {
}
