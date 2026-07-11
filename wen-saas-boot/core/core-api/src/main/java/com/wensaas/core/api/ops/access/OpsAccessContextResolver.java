package com.wensaas.core.api.ops.access;

/**
 * 运营访问上下文解析器：根据 userId 查 sys_role + sys_role_permission，合并多角色数据范围与权限点集合。
 *
 * <p>由 core-impl 提供 Bean；Web 层通过该接口拿到上下文（请求维度缓存在 {@code RequestContext} 内）。
 */
public interface OpsAccessContextResolver {

    /** 强制取当前请求的运营上下文；若 {@code UserContext} 未绑定运营账号则抛 IllegalStateException。 */
    OpsAccessContext require();

    /** 显式按 userId 解析（用于 fixture / 单测）。 */
    OpsAccessContext resolve(long userId);
}
