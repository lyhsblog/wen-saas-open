package com.wensaas.core.impl.permission.datascope;

import java.util.Set;

/**
 * 当前请求的数据权限上下文（ThreadLocal）。由 {@code DataPermissionAspect} 在
 * {@code @DataPermission} 方法调用前写入，在 {@code finally} 块清除。
 *
 * <p>{@code DataPermissionHandlerImpl} 在 MyBatis SQL 改写期间读取此上下文：
 * <ul>
 *   <li>上下文为空（无 {@code @DataPermission}）→ 不过滤</li>
 *   <li>{@code tables} 非空 → 只过滤声明的表；空集 → 过滤所有注册表</li>
 * </ul>
 */
public final class DataPermissionContext {

    /** 当前 action 及限定表集合。 */
    public record Context(String action, Set<String> tables) {}

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    public static void set(String action, String[] tables) {
        Set<String> tableSet = (tables == null || tables.length == 0) ? Set.of() : Set.of(tables);
        HOLDER.set(new Context(action, tableSet));
    }

    public static Context get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    private DataPermissionContext() {}
}
