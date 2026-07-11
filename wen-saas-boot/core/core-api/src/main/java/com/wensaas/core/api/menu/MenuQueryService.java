package com.wensaas.core.api.menu;

import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 菜单树查询服务。{@code sys_menu}（菜单树唯一权威）的双消费读侧：
 * <ul>
 *   <li>{@link #navTree} —— 导航树：按当前用户权限解析后的可见菜单（只含 GROUP/PAGE），
 *       供前端渲染侧栏；{@code CapabilitiesService.menus} 即由它派生。</li>
 *   <li>{@link #catalogTree} —— 角色权限目录全量树：不按用户过滤，容器做聚合三态、可授码为叶子，
 *       供角色页勾选。附 {@code unbound[]}（孤儿码：后端可授但无菜单引用，提示绑定用）。</li>
 * </ul>
 *
 * <p>两者都不再从权限 code 命名反推结构，而是从 {@code sys_menu} 的 GROUP/PAGE/ACTION 三类节点派生。
 */
public interface MenuQueryService {

    /** scope：端。1=OPS / 2=TENANT / 3=H5（与 {@code sys_menu.scope} 对齐）。 */
    int SCOPE_OPS = 1;
    int SCOPE_TENANT = 2;
    int SCOPE_H5 = 3;

    /**
     * 导航树（前端侧栏）。只回 GROUP/PAGE，ACTION 不进导航；空 GROUP 剔除。
     *
     * <p>可见性两道关，套餐门控优先于权限：
     * <ol>
     *   <li>套餐门控：PAGE 的 {@code permission_code} 属主是「受控业务模块」（声明非空 API 前缀）
     *       且不在 {@code enabledModuleCodes} 内 → 直接隐藏（连主管理员也不可见）；底座/ops 码不受此约束。</li>
     *   <li>权限：PAGE 可见 ⟺ {@code permission_code} 为 null（对所有人可见） 或 {@code owner}（主管理员）
     *       或 {@code userPermissions} 含该码。</li>
     * </ol>
     * GROUP 可见 ⟺ 存在可见子节点（未购模块叶子隐去后空组自动剔除）。按 {@code sort} 升序。
     *
     * @param scope              端
     * @param userPermissions    当前用户持有的权限码集合
     * @param owner              是否主管理员（true 则忽略权限码、全部可见；但仍受套餐门控约束）
     * @param enabledModuleCodes 当前租户已开通（active∪过期只读）的业务模块编码集合，用于套餐门控；
     *                           OPS 端传空集即可（ops 码非受控模块）
     */
    List<NavNode> navTree(int scope, Set<String> userPermissions, boolean owner, Set<String> enabledModuleCodes);

    /**
     * 角色权限目录全量树（角色页勾选）。不按当前用户权限过滤——全显以便分配；但仍受套餐门控。
     *
     * <p>投影：容器（GROUP / 有子动作的 PAGE）只做聚合三态、不带码；可授码一律是叶子——PAGE 的 gate 码
     * 投成「查看」叶子、ACTION 码各为叶子；无码且无动作的 PAGE（工作台/个人中心）不进此树。
     *
     * <p>套餐门控：受控业务模块未购时，其权限码叶子隐去、空容器剔除，且不进 {@code unbound}——
     * 没买的模块不该出现在角色勾选树里。{@code enabledModuleCodes} 同 {@link #navTree}。
     */
    CatalogTree catalogTree(int scope, Set<String> enabledModuleCodes);

    /**
     * 导航节点（GROUP/PAGE）。
     *
     * @param key      稳定键（{@code sys_menu.node_key}）
     * @param label    中文显示名
     * @param icon     图标名，可空
     * @param path     PAGE 路由；GROUP 为 null
     * @param sort     同级排序号
     * @param children 子节点
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class NavNode {
        private String key;
        private String label;
        private String icon;
        private String path;
        private int sort;
        private List<NavNode> children;

    }

    /**
     * 角色权限目录响应。
     *
     * @param tree    可勾选树（容器 + 叶子码）
     * @param unbound 孤儿码：后端可授但未被任何菜单引用（提示绑定用，不混进可勾树）
     */
    record CatalogTree(List<CatalogNode> tree, List<UnboundCode> unbound) {
    }

    /**
     * 角色目录节点。{@code code != null} 为可授叶子（勾它 = 授予该码）；{@code code == null} 为容器
     * （仅聚合三态、不可授）。
     *
     * @param key         树内唯一键（容器=node_key；动作叶子=node_key；PAGE 的查看叶子=node_key+".view"）
     * @param label       中文显示名
     * @param code        可授权限码；容器为 null
     * @param description 说明（角色页 tooltip），可空
     * @param children    子节点；叶子为 null
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class CatalogNode {
        private String key;
        private String label;
        private String code;
        private String description;
        private List<CatalogNode> children;

    }

    /**
     * 孤儿码（待绑定）。
     *
     * @param code        权限码
     * @param name        码名称
     * @param description 说明，可空
     */
    record UnboundCode(String code, String name, String description) {
    }
}
