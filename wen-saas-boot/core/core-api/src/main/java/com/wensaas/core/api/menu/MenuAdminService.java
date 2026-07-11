package com.wensaas.core.api.menu;

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
 * 菜单树管理（写）。运营「菜单管理」页对 {@code sys_menu} 的增改 / 移动 / 排序 / 启停 / 删除，
 * 权限 {@code ops.menu.manage}。
 *
 * <p>SEED 节点（{@code source=1}）仅可改属性 / 启停、不可删；ADMIN 节点（{@code source=2}）可删。
 * 保存校验：节点形状（GROUP/PAGE/ACTION 各自的 path/code/parent 约束）+ {@code permissionCode} 非空
 * 必须 ∈ {@link PermissionCodeCatalogService}（否则 {@code 422 UNKNOWN_PERMISSION_CODE}）。
 */
public interface MenuAdminService {

    /** scope 下管理用全量树（含停用节点 + id/source/dangling/status），按 sort 排序。 */
    List<MenuAdminNode> tree(int scope);

    /** 新建节点（node_key 服务端生成）。 */
    MenuAdminNode create(CreateMenuCommand cmd);

    /** 改属性（label/icon/path/permissionCode/description/sort）；node_type/scope 不可改。 */
    MenuAdminNode update(long id, UpdateMenuCommand cmd);

    /** 移动 / 排序：改 parent_key + sort。 */
    void move(long id, String parentKey, int sort);

    /** 启停。 */
    MenuAdminNode setStatus(long id, boolean enabled);

    /** 删除（仅 ADMIN 节点、且无子节点）。 */
    void delete(long id);

    /**
     * 新建命令。
     *
     * @param scope          端（必填）
     * @param parentKey      父节点 node_key；顶层为 null
     * @param nodeType       1=GROUP / 2=PAGE / 3=ACTION
     * @param label          中文显示名
     * @param icon           图标名（GROUP/PAGE）
     * @param path           PAGE 路由
     * @param permissionCode PAGE 的 gate 码 / ACTION 的码
     * @param description    说明
     * @param sort           同级排序
     */
    record CreateMenuCommand(Integer scope, String parentKey, Integer nodeType, String label,
                             String icon, String path, String permissionCode, String description, Integer sort) {
    }

    /** 改属性命令（不含 scope/nodeType/parentKey）。 */
    record UpdateMenuCommand(String label, String icon, String path, String permissionCode,
                             String description, Integer sort) {
    }

    /**
     * 菜单管理节点（管理视图，含全部字段 + 子节点）。
     *
     * @param id             雪花 → string
     * @param scope          端
     * @param nodeKey        稳定键
     * @param parentKey      父节点 key
     * @param nodeType       1=GROUP / 2=PAGE / 3=ACTION
     * @param label          中文显示名
     * @param icon           图标名
     * @param path           PAGE 路由
     * @param permissionCode 绑定权限码
     * @param description    说明
     * @param sort           同级排序
     * @param source         1=SEED / 2=ADMIN
     * @param status         1=ENABLED / 0=DISABLED
     * @param dangling       permissionCode 悬空失效（启动校验置位）
     * @param children       子节点
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class MenuAdminNode {
        private String id;
        private int scope;
        private String nodeKey;
        private String parentKey;
        private int nodeType;
        private String label;
        private String icon;
        private String path;
        private String permissionCode;
        private String description;
        private int sort;
        private int source;
        private int status;
        private boolean dangling;
        private LocalDateTime updatedAt;
        private List<MenuAdminNode> children;

    }
}
