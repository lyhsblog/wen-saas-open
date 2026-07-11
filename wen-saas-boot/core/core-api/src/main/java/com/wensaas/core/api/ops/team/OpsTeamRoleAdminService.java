package com.wensaas.core.api.ops.team;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 运营团队角色管理。
 *
 * <p>2 个 systemPreset 角色（OPS_SUPER_ADMIN / OPS_CSM）由迁移种入，
 * 禁止删除 / 改 code，可改权限点 + 数据范围 + 成员分配。
 *
 * <p>数据范围 {@code dataScope}: "ALL" → {@code OpsDataScope.OPS_ALL}（DB 1）；
 * "OWNED" → {@code OpsDataScope.OPS_OWNED}（DB 3）。
 */
public interface OpsTeamRoleAdminService {

    /** 列表（按 systemPreset desc + created_at desc）。 */
    List<OpsRoleListItem> listRoles();

    /** 详情。 */
    OpsRoleDetail getRole(long roleId);

    /** 新建自定义角色。 */
    OpsRoleDetail create(CreateRoleCommand cmd);

    /** 编辑基本信息（不含权限 / 成员）。 */
    OpsRoleDetail update(long roleId, UpdateRoleCommand cmd);

    /** 删除（仅自定义；要求无成员）。 */
    void delete(long roleId);

    /** 全量替换权限点。 */
    OpsRoleDetail updatePermissions(long roleId, List<String> permissionCodes);

    /** 列成员。 */
    List<MemberRef> listMembers(long roleId);

    /** 全量替换成员（diff）。 */
    void assignMembers(long roleId, List<Long> userIds);

    // ===== records =====

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class OpsRoleListItem {
        private long id;
        private String code;
        private String name;
        private String description;
        private String dataScope;
        private int status;
        private int memberCount;
        private int permissionCount;
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
    static class OpsRoleDetail {
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

    record CreateRoleCommand(
            String code,
            String name,
            String description,
            String dataScope
    ) {}

    record UpdateRoleCommand(
            String name,
            String description,
            String dataScope
    ) {}

    record MemberRef(long userId, String username, String realName) {}

    /**
     * 权限树节点（一层，按 code 分组到树形交给前端展开）。
     *
     * @param code 权限点编码（如 "ops.tenant.list"）
     * @param name 显示名
     * @param description 描述
     * @param group 分组（按 code 第二段切分，如 "tenant" / "package" / "setting"）
     */
}
