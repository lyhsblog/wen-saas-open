package com.wensaas.core.api.spi.def;

import java.util.Objects;

/**
 * 数据权限参与表声明。
 *
 * <p>业务模块在 {@code BusinessModuleDescriptor#dataPermissionTables()} 中声明哪些业务表参与
 * 数据权限过滤；底座的 MyBatis 拦截器据此自动注入 {@code WHERE creatorColumn = ?} 或
 * {@code WHERE deptColumn IN (..)}。
 *
 * <p>{@code resource} / {@code resourceName} 仅用于配置 UI 分组与启动期护栏，<b>不参与 runtime
 * scope 解析</b>——过滤触发与 scope 完全由请求级 {@code @DataPermission} 的 action 上下文决定
 * （handler 不做任何前缀匹配）。
 *
 * @param resource      资源码，规范 {@code {module}.{menu}}，与权限点 {@code PermissionDef.code} 前缀对齐
 * @param resourceName  资源显示名（配置 UI 用）
 * @param tableName     物理表名（推荐小写；运行时按小写匹配）
 * @param creatorColumn 归属列名（SELF 用）。审计/管理类传 {@code created_by}；可分配资源传 {@code owner_id}
 * @param deptColumn    数据归属部门列名（"本部门+下属"范围使用）；可空。
 *                      为空时表示该表无部门归属信息，DEPT_AND_BELOW 范围降级为 {@code 1=0}
 *                      （不可见，安全保守默认）。
 */
public record DataPermissionTableDef(String resource, String resourceName, String tableName,
                                     String creatorColumn, String deptColumn) {

    public DataPermissionTableDef {
        requireNonBlank(resource, "resource");
        requireNonBlank(resourceName, "resourceName");
        requireNonBlank(tableName, "tableName");
        requireNonBlank(creatorColumn, "creatorColumn");
        // deptColumn 允许 null；非 null 时要求非空白
        if (deptColumn != null && deptColumn.isBlank()) {
            throw new IllegalArgumentException("deptColumn 不能为空白（如不需要请传 null）");
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
    }
}
