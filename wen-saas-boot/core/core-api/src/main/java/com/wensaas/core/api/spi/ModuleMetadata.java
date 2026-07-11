package com.wensaas.core.api.spi;

import java.util.List;
import java.util.Objects;

/**
 * 业务模块元数据。
 *
 * @param code 模块编码（如 "crm"），全局唯一，遵循 {@code [a-z][a-z0-9_]*}
 * @param name 显示名（中文友好）
 * @param description 简要描述
 * @param version 语义化版本（如 "1.2.0"）
 * @param icon 图标路径
 * @param dependencies 依赖的其他业务模块编码列表
 */
public record ModuleMetadata(
        String code,
        String name,
        String description,
        String version,
        String icon,
        List<String> dependencies) {

    public ModuleMetadata {
        Objects.requireNonNull(code, "module code must not be null");
        Objects.requireNonNull(name, "module name must not be null");
        Objects.requireNonNull(version, "module version must not be null");
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    /**
     * 最小工厂方法：只指定必填字段。
     */
    public static ModuleMetadata of(String code, String name, String version) {
        return new ModuleMetadata(code, name, null, version, null, List.of());
    }
}
