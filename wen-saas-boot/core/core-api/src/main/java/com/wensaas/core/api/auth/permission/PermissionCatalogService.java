package com.wensaas.core.api.auth.permission;

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
 * 功能权限目录（读）API。
 *
 * <p>返回租户后台「角色 → 功能权限 Tab」的三层权限点树：
 * <strong>模块 → 菜单 → 操作权限点</strong>。
 *
 * <p>权限点来源：
 * <ul>
 *   <li><b>底座权限点</b>：底座代码声明（通讯录 / 系统管理 等），由本服务静态维护，
 *       与各 {@code @RequirePermission} 实际拦截的 code 严格一致；</li>
 *   <li><b>业务模块权限点</b>：业务模块经 SPI {@code BusinessModuleDescriptor.permissions()}
 *       注册到 {@code PermissionRegistry}，按归属模块动态拼接在底座之后。</li>
 * </ul>
 *
 * <p>运营专属 {@code ops.*} 权限点不出现在租户目录里。
 */
public interface PermissionCatalogService {

    /** 返回当前租户可见的功能权限点目录树。 */
    List<CatalogModule> listCatalog();

    /**
     * 列出所有通过 {@code @DataPermission} 标注的功能点 code 及其名称。
     *
     * <p>前端「角色数据范围配置」页用此接口构建「按功能点单独配置」的 action 选择列表。
     * 名称优先从 {@link com.wensaas.core.api.spi.def.PermissionDef} 解析，未注册则直接返回 code。
     */
    List<DataPermissionActionDef> listDataPermissionActions();

    /** 数据权限 action 描述（用于前端选择列表）。 */
    record DataPermissionActionDef(String code, String name) {
    }

    /** 模块层（业务模块 / 底座功能域）。 */
    record CatalogModule(String key, String name, boolean base, List<CatalogMenu> menus) {
    }

    /** 菜单层。 */
    record CatalogMenu(String key, String name, List<CatalogPoint> points) {
    }

    /** 操作权限点层。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class CatalogPoint {
        private String code;
        private String name;
        private String description;

    }
}
