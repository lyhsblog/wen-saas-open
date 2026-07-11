package com.wensaas.core.api.auth.permission;

import java.util.List;

/**
 * 用户 ↔ 角色指派关系读服务：permission domain 对外暴露的角色成员关系查询入口。
 *
 * <p>跨 domain 读取角色指派（{@code sys_role} / {@code sys_user_role}）一律走本服务，
 * <b>不得直接注入对应 {@code *Mapper}</b>。仅查「用户-角色」成员关系，不负责角色定义的增删改查
 * （那在 {@code RoleAdminService} / {@code PermissionCatalogService}）。
 *
 * <p>租户范围：带 {@code tenantId} 入参者按入参过滤；{@link #userIdsByRole} 不带，依赖调用方租户
 * 上下文（受 MP TenantLine 注入约束），本服务不另加旁路——有意为之的行为保持。
 */
public interface UserRoleQueryService {

    /** 用户在指定租户下的角色（保持底层返回序）。 */
    List<RoleBrief> rolesOfUser(long tenantId, long userId);

    /** 按角色 id 批量取摘要（限定租户；null / 未命中的 id 静默缺席）。 */
    List<RoleBrief> rolesByIds(long tenantId, List<Long> roleIds);

    /** 角色下的成员 userId（可能含重复，由调用方按需去重）。依赖调用方租户上下文。 */
    List<Long> userIdsByRole(long roleId);

    /** 角色摘要：{@code id} 供按角色取数据范围，{@code code/name} 供展示。 */
    record RoleBrief(long id, String code, String name) {}
}
