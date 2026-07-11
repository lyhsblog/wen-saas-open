package com.wensaas.core.api.auth.permission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 角色管理（写）API。
 *
 * <p>租户上下文：所有方法从 {@code TenantContext.require()} 取 {@code tenantId}。
 *
 * <p>主管理员角色（code={@code SYSTEM_OWNER}）由 {@code tenantOwner} 字段标记，
 * 完全只读；其它系统预置角色（管理员 / 普通员工）允许改权限点 / 数据范围但禁止删除。
 *
 * <p>权限点写入后 {@link com.wensaas.core.api.auth.permission.RoleAdminService#updatePermissions}
 * 内会 {@code permissionResolver.evictAll()}（v1 简化）。
 */
public interface RoleAdminService {

    /** 列出当前租户的角色（按 created_at desc）。 */
    List<RoleView> listRoles();

    /** 取角色详情（含 memberCount + permissionCodes）。 */
    RoleDetail getRole(long roleId);

    /** 新建自定义角色。 */
    RoleDetail create(CreateRoleRequest req);

    /** 编辑角色基本信息（不含权限点 / 成员）。 */
    RoleDetail update(long roleId, UpdateRoleRequest req);

    /** 删除角色（仅自定义；要求无成员）。 */
    void delete(long roleId);

    /** 启用 / 停用角色。 */
    RoleDetail setStatus(long roleId, boolean enabled);

    /** 全量替换角色权限点。 */
    RoleDetail updatePermissions(long roleId, List<String> permissionCodes);

    /** 列角色成员（含 user_id 与展示用 username / realName）。 */
    List<MemberRef> listMembers(long roleId);

    /** 批量分配 / 移除成员（按 ids set 算差异）。 */
    RoleDetail assignMembers(long roleId, List<Long> userIds);

    /** 仅移除指定成员。 */
    RoleDetail removeMembers(long roleId, List<Long> userIds);

    /**
     * 取角色当前数据范围配置（默认 + action 覆盖）。
     *
     * <p>数据来源：{@code sys_role_data_scope}；action=NULL 行为角色默认，非 NULL 行为 action 覆盖。
     */
    RoleDataScopeView getRoleDataScope(long roleId);

    /**
     * 全量保存角色数据范围配置。
     *
     * <p>策略：删除该角色所有 {@code sys_role_data_scope} 行，再按 cmd 重新插入；
     * 同步更新 {@code sys_role.data_scope}（保证列表页展示的"默认范围"与配置一致）；
     * 写后 {@code permissionResolver.evictAll()}。
     */
    void saveRoleDataScope(long roleId, SaveDataScopeCmd cmd);

    // ===================== records =====================

    /** 角色数据范围配置视图（读）。 */
    record RoleDataScopeView(String defaultScope, Map<String, String> actionOverrides) {
    }

    /** 角色数据范围配置保存命令（写）。 */
    record SaveDataScopeCmd(String defaultScope, Map<String, String> actionOverrides) {
    }

    record CreateRoleRequest(
            String code,
            String name,
            String description,
            String dataScope /* ALL | DEPT_AND_BELOW | SELF */) {
    }

    record UpdateRoleRequest(
            String name,
            String description,
            String dataScope) {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class RoleView {
        private long id;
        private String code;
        private String name;
        private String description;
        private String dataScope;
        private int status;
        private int memberCount;
        private boolean systemPreset;
        private LocalDateTime createdAt;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class RoleDetail {
        private long id;
        private String code;
        private String name;
        private String description;
        private String dataScope;
        private int status;
        private int memberCount;
        private boolean systemPreset;
        private List<String> permissions;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

    }

    record MemberRef(long userId, String username, String realName) {
    }
}
