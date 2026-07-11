package com.wensaas.core.api.auth.user;

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
 * 当前用户能力包（前端登录后立即拉取）。
 *
 * <p>从 {@link UserContext#require()} 取认证主体；不接受外部参数避免被滥用查询他人。
 *
 * <p>聚合数据源：
 * <ul>
 *   <li>{@code permissions / dataScope / visibleDeptIds} ← {@code PermissionResolver}（Redis 缓存 TTL 1h）</li>
 *   <li>{@code enabledModules} ← {@code sys_business_module.status=2}（v1 平台全部，待"租户套餐"会话补租户维度）</li>
 *   <li>{@code activePackages} ← {@code sys_tenant.plans jsonb}（v1 字段未建，永远返空数组）</li>
 *   <li>{@code menus} ← {@code MenuQueryService#navTree} 从 {@code sys_menu} 按权限+套餐门控派生</li>
 * </ul>
 */
public interface CapabilitiesService {

    /** 解析当前用户能力包。从 {@link UserContext#require()} 取主体。 */
    Capabilities resolve();

    /**
     * 能力包响应。
     *
     * @param userId          用户 ID（雪花 → string）
     * @param tenantId        租户 ID（雪花 → string；运营账号为 "0"）
     * @param tenantOwner     是否主管理员
     * @param permissions     权限点集合（已排序，便于前端 diff）
     * @param dataScope       ALL / DEPT_AND_BELOW / SELF
     * @param visibleDeptIds  DEPT_AND_BELOW 时含子孙部门 ID（已字符串化 + 排序）；ALL/SELF 时为空
     * @param enabledModules  已上线业务模块
     * @param activePackages  已勾选套餐包（v1 永远空数组）
     * @param menus           可见菜单树
     * @param impersonating   当前会话是否为运营代登录态（{@code Session.originalSessionId != null}）
     * @param operatorName    代登录态下发起代登录的运营操作人登录名；非代登录态为 null。供前端水印 / 标识溯源
     * @param realName        当前用户真实姓名（sys_user.real_name，未加密）；供前端防截屏水印展示，可空
     * @param screenshotWatermark 防截屏水印是否开启（租户级开关，默认开启）；前端据此决定是否平铺可溯源水印
     */
    record Capabilities(
            String userId,
            String tenantId,
            boolean tenantOwner,
            List<String> permissions,
            String dataScope,
            List<String> visibleDeptIds,
            List<ModuleView> enabledModules,
            List<PackageView> activePackages,
            List<MenuNode> menus,
            boolean impersonating,
            String operatorName,
            String realName,
            boolean screenshotWatermark) {
    }

    /**
     * 业务模块视图。
     *
     * @param code     模块编码
     * @param name     模块名
     * @param version  SemVer 版本
     * @param readonly 是否只读（套餐过期降级时为 true；v1 永远 false）
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class ModuleView {
        private String code;
        private String name;
        private String version;
        private boolean readonly;

    }

    /**
     * 套餐包视图（v1 永远空数组，待 {@code sys_tenant.plans} 字段建好）。
     *
     * @param code      套餐编码
     * @param name      套餐名
     * @param expiresAt 到期时间 ISO-8601；null 表示永久
     * @param readonly  是否只读
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class PackageView {
        private String code;
        private String name;
        private String expiresAt;
        private boolean readonly;

    }

    /**
     * 菜单树节点。
     *
     * @param path     菜单路径（前端 React Router 直接 navigate）
     * @param name     显示名
     * @param icon     图标，可空
     * @param sort     排序号
     * @param children 子菜单
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class MenuNode {
        private String path;
        private String name;
        private String icon;
        private int sort;
        private List<MenuNode> children;

    }
}
