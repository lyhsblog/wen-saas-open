package com.wensaas.core.impl.permission.eval;

import java.util.Collection;

/**
 * 用户有效权限解析器。按「用户 + 权限点」维度定向取数：每次只查当前要判定的权限点 /
 * 功能点，不再装配用户全量权限。结果在请求内复用（{@link RequestScopedPermissionResolver}），跨请求不缓存——
 * 权限/角色/范围每请求直查 DB（一条索引覆盖的连表），唯独整租户部门树仍跨请求缓存（见 {@link PermissionCacheEvictor}）。
 *
 * <p>三类用法：
 * <ul>
 *   <li>{@link #has} / {@link #hasAny}：功能权限点判定（AOP 切面热路径）</li>
 *   <li>{@link #scopeFor}：单 action 的有效数据范围（数据权限拦截器）</li>
 *   <li>{@link #resolve}：用户全量权限点 + 默认范围（登录态 capabilities / 运营访问上下文，每登录一次）</li>
 * </ul>
 */
public interface PermissionResolver {

    /** 用户是否拥有指定权限点（启用且未软删的角色中任一授予即可）。 */
    boolean has(long tenantId, long userId, String code);

    /** 用户是否拥有 codes 中任一权限点；codes 为空返回 false。 */
    boolean hasAny(long tenantId, long userId, Collection<String> codes);

    /** 指定 action 的有效数据范围：在「授予该 action 权限点」的角色内取最宽，并物化好部门集合。 */
    EffectiveScope scopeFor(long tenantId, long userId, String action);

    /** 用户全量权限点 + 默认数据范围（+ 默认范围为 DEPT_AND_BELOW 时的可见部门）。 */
    UserPermissions resolve(long tenantId, long userId);
}
