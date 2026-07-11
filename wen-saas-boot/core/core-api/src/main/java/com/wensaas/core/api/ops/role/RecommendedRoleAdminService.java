package com.wensaas.core.api.ops.role;

import com.wensaas.core.api.tenant.CrossTenantQuery;

import java.time.Instant;
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
 * 运营后台 · 推荐角色管理。
 *
 * <p>「推荐角色管理」是「套餐与功能」下第三个子页（与套餐包管理并列）。
 * 由运营维护一批"开箱即用的角色模板"，租户启用某关联套餐包后即在其后台
 * 「推荐角色」分组看到模板，一键生成快照角色。
 *
 * <p>本接口仅覆盖运营端 admin CRUD：模板 CRUD + 3 态状态流转（草稿/已发布/已下架）
 * + 关联套餐包（多选，≥1）+ 包含权限点（跨业务模块，非 ops.*）+ 复制。
 * 租户端「启用推荐角色 → 生成快照角色」归租户角色模块，不在本接口范围。
 *
 * <p>已发布（{@code published}）后的编辑限制（保护已启用客户）：
 * <ul>
 *   <li>允许改名、改描述、改默认数据范围（仅影响后续启用）、<b>加</b>权限点、<b>加</b>关联套餐包</li>
 *   <li><b>禁止减</b>权限点（{@code PERMISSION_REMOVE_FORBIDDEN}）</li>
 *   <li><b>禁止减</b>关联套餐包（{@code PACKAGE_REMOVE_FORBIDDEN}）</li>
 * </ul>
 *
 * <p>删除：仅当模板从未被任何租户启用过才允许（{@code TEMPLATE_IN_USE} 否则只能下架）。
 *
 * <p>所涉表均在 {@code platform-only-tables} 白名单，无需 {@code @CrossTenantQuery}。
 */
public interface RecommendedRoleAdminService {

    /** 列表（按状态筛选 + 名称关键字过滤；默认 status=ALL，按状态优先 + 创建时间倒序排序）。 */
    List<TemplateListItem> listTemplates(StatusFilter status, String keyword);

    /**
     * 详情（含 packageCodes / permissionCodes / 已启用租户数 / 关联套餐包富信息 / 失效权限点 / 全归档标记）。
     *
     * <p>OPS-R-023 惰性翻转：若模板为 {@code PUBLISHED} 且其关联套餐包全部已归档，
     * 读取时自动转 {@code INACTIVE} 后再返回（保护新租户不再看到无效推荐）。
     */
    TemplateDetail getTemplate(long id);

    /**
     * 某模板的已启用租户列表（分页，启用时间倒序）。OPS-R-020。
     *
     * <p>数据源 {@code sys_recommended_role_template_activation}：租户端「启用推荐角色 → 生成快照角色」
     * 实现后写入；当前该表无数据，故本接口现阶段恒返回空页（{@code total=0}）。
     *
     * @throws RecommendedRoleAdminException TEMPLATE_NOT_FOUND
     */
    @CrossTenantQuery("RecommendedRoleAdmin: 已启用租户列表需跨租户读 sys_user 解析客户经理名")
    ActivationPage listActivations(long id, int page, int pageSize);

    /**
     * 创建模板。
     *
     * @throws RecommendedRoleAdminException NAME_CONFLICT / NO_PACKAGE_ASSOCIATED / PACKAGE_NOT_FOUND
     *                                       / INVALID_PERMISSION_CODE / INVALID_DATA_SCOPE
     */
    TemplateDetail create(CreateCommand cmd);

    /**
     * 编辑模板。null 字段表示不改；packageCodes / permissionCodes 非 null 时全量替换。
     *
     * @throws RecommendedRoleAdminException TEMPLATE_NOT_FOUND / NAME_CONFLICT
     *                                       / PERMISSION_REMOVE_FORBIDDEN / PACKAGE_REMOVE_FORBIDDEN
     *                                       / PACKAGE_NOT_FOUND / INVALID_PERMISSION_CODE / INVALID_DATA_SCOPE
     */
    TemplateDetail update(long id, UpdateCommand cmd);

    /**
     * 发布（draft / inactive → published）。要求已关联 ≥ 1 个套餐包。
     *
     * @throws RecommendedRoleAdminException TEMPLATE_NOT_FOUND / NO_PACKAGE_ASSOCIATED
     */
    void publish(long id);

    /**
     * 下架（published → inactive）。已启用客户不受影响。
     *
     * @throws RecommendedRoleAdminException TEMPLATE_NOT_FOUND / INVALID_STATUS_TRANSITION
     */
    void unpublish(long id);

    /**
     * 删除（仅在从未被任何租户启用过时允许）。
     *
     * @throws RecommendedRoleAdminException TEMPLATE_NOT_FOUND / TEMPLATE_IN_USE
     */
    void delete(long id);

    /**
     * 复制模板（以源模板的 description/dataScope/packageCodes/permissionCodes 为初值，重填 name）。
     * 新模板恒为 {@code DRAFT}（草稿）。
     *
     * @throws RecommendedRoleAdminException TEMPLATE_NOT_FOUND（源不存在）/ NAME_CONFLICT（新名已存在）
     */
    TemplateDetail copy(long sourceId, CopyCommand cmd);

    /**
     * 可勾选的权限点目录树（租户域，<b>不含 ops.*</b>）。直接复用 {@code sys_menu} 派生的
     * {@link com.wensaas.core.api.menu.MenuQueryService#catalogTree}（中文分组 + 三态 + 可授码叶子），
     * 与租户/运营角色页同源；推荐角色是跨租户模板，<b>不做套餐门控</b>（全量码可选，落到具体租户才被套餐约束）。
     */
    com.wensaas.core.api.menu.MenuQueryService.CatalogTree listPermissionCatalog();

    /** 可关联的套餐包清单（供新建/编辑多选）。 */
    List<PackageRef> listAvailablePackages();

    // ===================== records & enums =====================

    /** 列表筛选枚举。{@code ALL} = 不筛选（默认）。 */
    enum StatusFilter { DRAFT, PUBLISHED, INACTIVE, ALL }

    /**
     * 模板状态（对外口径统一大写；整型 {@code code()} 对齐 {@code sys_recommended_role_template.status} 落库值）。
     */
    enum TemplateStatus {
        DRAFT(0), PUBLISHED(1), INACTIVE(2);

        private final int code;

        TemplateStatus(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        /** 由落库整型还原枚举；未知值抛 {@link IllegalArgumentException}。 */
        public static TemplateStatus fromCode(int code) {
            for (TemplateStatus s : values()) {
                if (s.code == code) {
                    return s;
                }
            }
            throw new IllegalArgumentException("未知推荐角色模板状态码: " + code);
        }
    }

    /** 列表项（精简视图）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TemplateListItem {
        private long id;
        private String name;
        private String description;
        private TemplateStatus status;
        private int dataScope;
        private List<String> packageCodes;
        private int permissionCount;
        private int activatedTenantCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    /**
     * 详情。
     *
     * @param packageCodes          关联套餐包编码（供编辑表单回填，排序后）
     * @param permissionCodes       包含的权限点编码（排序后）
     * @param packages              关联套餐包富信息（OPS-R-021 每包已勾选租户数 + OPS-R-023 归档标记）
     * @param invalidPermissionCodes 已落库但当前未在权限注册表中的权限点（OPS-R-022 部分权限失效，业务模块卸载所致）
     * @param allPackagesArchived   关联套餐包是否全部已归档（OPS-R-023）；非空且全归档时为 true
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class TemplateDetail {
        private long id;
        private String name;
        private String description;
        private TemplateStatus status;
        private int dataScope;
        private List<String> packageCodes;
        private List<String> permissionCodes;
        private List<AssociatedPackage> packages;
        private List<String> invalidPermissionCodes;
        private boolean allPackagesArchived;
        private int activatedTenantCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    /**
     * 关联套餐包富信息（详情专用）。
     *
     * @param code        套餐编码
     * @param name        套餐名（套餐已删则降级为 code）
     * @param isSystem    是否基础包
     * @param status      套餐当前状态（DRAFT/ACTIVE/INACTIVE/ARCHIVED；套餐已删为 null）
     * @param archived    是否已归档（OPS-R-023）
     * @param tenantCount 已勾选该套餐的租户数（OPS-R-021）
     */
    record AssociatedPackage(
            String code,
            String name,
            boolean isSystem,
            String status,
            boolean archived,
            int tenantCount
    ) {}

    /** 已启用租户列表分页（OPS-R-020）。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ActivationPage {
        private List<ActivationItem> items;
        private long total;
        private int page;
        private int pageSize;
    }

    /**
     * 已启用租户项（含租户内快照角色名 / 已分配成员数富列）。
     *
     * @param tenantId      租户 id
     * @param tenantName    租户名
     * @param tenantCode    租户标识
     * @param csmName       客户经理显示名（可空）
     * @param createdRoleId 启用时生成的快照角色 id（可空，租户端写入）
     * @param roleName      租户内快照角色名（可空：快照角色已被租户删除时为陈旧记录）
     * @param memberCount   该快照角色当前已分配成员数（角色已删或未分配时为 0）
     * @param activatedAt   启用时间
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ActivationItem {
        private long tenantId;
        private String tenantName;
        private String tenantCode;
        private String csmName;
        private Long createdRoleId;
        private String roleName;
        private int memberCount;
        private Instant activatedAt;
    }

    /** 可关联套餐包项（供新建/编辑多选）。 */
    record PackageRef(
            String code,
            String name,
            String status,
            boolean isSystem
    ) {}

    /**
     * 创建命令。
     *
     * @param name               名称；非空，运营内部唯一
     * @param description        描述；可空
     * @param dataScope          默认数据范围；1=全部 / 2=本部门含下属 / 3=仅本人
     * @param packageCodes       关联套餐包编码（≥ 1 个）
     * @param permissionCodes    包含的权限点编码（跨业务模块，非 ops.*）；可空
     * @param publishImmediately true=直接发布（status=published）；false=保存为草稿
     */
    record CreateCommand(
            String name,
            String description,
            int dataScope,
            List<String> packageCodes,
            List<String> permissionCodes,
            boolean publishImmediately
    ) {}

    /**
     * 编辑命令。null 字段表示不改。
     *
     * @param name            可空（不改）
     * @param description     可空（不改）
     * @param dataScope       可空（不改）；非 null 时校验 1/2/3
     * @param packageCodes    可空：null=不改；非 null（含空集合）=全量替换（已发布禁止减）
     * @param permissionCodes 可空：null=不改；非 null（含空集合）=全量替换（已发布禁止减）
     */
    record UpdateCommand(
            String name,
            String description,
            Integer dataScope,
            List<String> packageCodes,
            List<String> permissionCodes
    ) {}

    /**
     * 复制命令。
     *
     * @param name 新模板名称；非空，运营内部唯一
     */
    record CopyCommand(
            String name
    ) {}
}
