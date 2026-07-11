package com.wensaas.core.impl.menu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.menu.MenuQueryService;
import com.wensaas.core.api.menu.PermissionCodeCatalogService;
import com.wensaas.core.api.menu.PermissionCodeCatalogService.PermissionCodeView;
import com.wensaas.core.impl.pkg.PackageModuleGate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link MenuQueryService} 默认实现。从 {@code sys_menu} 派生导航树 / 角色目录树。
 *
 * <p>导航与角色目录都先按 {@code (scope, status=ENABLED)} 取节点、按 {@code parent_key} 建树、{@code sort}
 * 排序，再各自投影：
 * <ul>
 *   <li>导航 {@link #navTree}：只出 GROUP/PAGE，PAGE 按 gate 码 + owner 判可见，GROUP 按"有可见子"裁剪。</li>
 *   <li>角色目录 {@link #catalogTree}：容器只聚合、可授码投成叶子；{@code unbound} = 后端可授码 − 菜单已引用码。</li>
 * </ul>
 */
public class MenuQueryServiceImpl implements MenuQueryService {

    private static final int NODE_GROUP = 1;
    private static final int NODE_PAGE = 2;
    private static final int NODE_ACTION = 3;
    private static final int STATUS_ENABLED = 1;

    private final SysMenuMapper menuMapper;
    private final PermissionCodeCatalogService codeCatalog;
    private final PackageModuleGate packageGate;

    public MenuQueryServiceImpl(SysMenuMapper menuMapper, PermissionCodeCatalogService codeCatalog,
                                PackageModuleGate packageGate) {
        this.menuMapper = menuMapper;
        this.codeCatalog = codeCatalog;
        this.packageGate = packageGate;
    }

    // ===== 导航树 =====

    @Override
    public List<NavNode> navTree(int scope, Set<String> userPermissions, boolean owner, Set<String> enabledModuleCodes) {
        Set<String> perms = userPermissions == null ? Set.of() : userPermissions;
        Set<String> enabled = enabledModuleCodes == null ? Set.of() : enabledModuleCodes;
        Set<String> gated = packageGate.gatedModuleCodes();
        Map<String, List<SysMenu>> byParent = loadByParent(scope);
        return buildNav(byParent, null, perms, owner, enabled, gated);
    }

    private List<NavNode> buildNav(Map<String, List<SysMenu>> byParent, String parentKey,
                                   Set<String> perms, boolean owner, Set<String> enabled, Set<String> gated) {
        List<NavNode> out = new ArrayList<>();
        for (SysMenu m : childrenOf(byParent, parentKey)) {
            if (m.getNodeType() == NODE_ACTION) {
                continue; // 动作不进导航
            }
            List<NavNode> children = buildNav(byParent, m.getNodeKey(), perms, owner, enabled, gated);
            if (m.getNodeType() == NODE_GROUP) {
                if (!children.isEmpty()) { // 空组剔除
                    out.add(node(m, children));
                }
            } else if (pageVisible(m, perms, owner, enabled, gated) || !children.isEmpty()) {
                out.add(node(m, children));
            }
        }
        return out;
    }

    private boolean pageVisible(SysMenu page, Set<String> perms, boolean owner,
                                Set<String> enabled, Set<String> gated) {
        String code = page.getPermissionCode();
        if (packageHidden(code, enabled, gated)) {
            return false; // 套餐门控优先于 owner / 持码：未购受控模块直接隐藏
        }
        return code == null || owner || perms.contains(code);
    }

    private boolean packageHidden(String code, Set<String> enabled, Set<String> gated) {
        return packageGate.hidden(code, enabled, gated);
    }

    private static NavNode node(SysMenu m, List<NavNode> children) {
        return new NavNode(m.getNodeKey(), m.getLabel(), m.getIcon(), m.getPath(),
                m.getSort() == null ? 0 : m.getSort(), children);
    }

    // ===== 角色权限目录树 =====

    @Override
    public CatalogTree catalogTree(int scope, Set<String> enabledModuleCodes) {
        Set<String> enabled = enabledModuleCodes == null ? Set.of() : enabledModuleCodes;
        Set<String> gated = packageGate.gatedModuleCodes();
        Map<String, List<SysMenu>> byParent = loadByParent(scope);
        List<CatalogNode> tree = buildCatalog(byParent, null, enabled, gated);
        return new CatalogTree(tree, computeUnbound(scope, byParent, enabled, gated));
    }

    private List<CatalogNode> buildCatalog(Map<String, List<SysMenu>> byParent, String parentKey,
                                           Set<String> enabled, Set<String> gated) {
        List<CatalogNode> out = new ArrayList<>();
        for (SysMenu m : childrenOf(byParent, parentKey)) {
            switch (m.getNodeType()) {
                case NODE_GROUP -> {
                    List<CatalogNode> children = buildCatalog(byParent, m.getNodeKey(), enabled, gated);
                    if (!children.isEmpty()) {
                        out.add(container(m, children));
                    }
                }
                case NODE_PAGE -> {
                    List<SysMenu> actions = childrenOf(byParent, m.getNodeKey()).stream()
                            .filter(c -> c.getNodeType() == NODE_ACTION)
                            .toList();
                    String gate = m.getPermissionCode();
                    if (!actions.isEmpty()) {
                        // 有子动作的 PAGE = 容器：gate 码投成「查看」叶子 + 各动作叶子（未购模块码隐去）
                        List<CatalogNode> children = new ArrayList<>();
                        if (gate != null && !packageHidden(gate, enabled, gated)) {
                            children.add(new CatalogNode(m.getNodeKey() + ".view", "查看", gate,
                                    m.getDescription(), null));
                        }
                        for (SysMenu a : actions) {
                            if (!packageHidden(a.getPermissionCode(), enabled, gated)) {
                                children.add(new CatalogNode(a.getNodeKey(), a.getLabel(),
                                        a.getPermissionCode(), a.getDescription(), null));
                            }
                        }
                        if (!children.isEmpty()) { // 码全被门控隐去则容器一并剔除
                            out.add(container(m, children));
                        }
                    } else if (gate != null && !packageHidden(gate, enabled, gated)) {
                        // 无子动作但带 gate 码的 PAGE = 单个可授叶子
                        out.add(new CatalogNode(m.getNodeKey(), m.getLabel(), gate, m.getDescription(), null));
                    }
                    // 无码且无动作的 PAGE（工作台/个人中心）不进树
                }
                default -> { /* ACTION 已在所属 PAGE 下处理 */ }
            }
        }
        return out;
    }

    private static CatalogNode container(SysMenu m, List<CatalogNode> children) {
        return new CatalogNode(m.getNodeKey(), m.getLabel(), null, null, children);
    }

    /** unbound = 后端可授码（{@link PermissionCodeCatalogService}） − 菜单已引用码 − 未购受控模块码。 */
    private List<UnboundCode> computeUnbound(int scope, Map<String, List<SysMenu>> byParent,
                                             Set<String> enabled, Set<String> gated) {
        Set<String> referenced = byParent.values().stream()
                .flatMap(List::stream)
                .map(SysMenu::getPermissionCode)
                .filter(c -> c != null)
                .collect(Collectors.toSet());

        List<UnboundCode> out = new ArrayList<>();
        for (PermissionCodeView v : codeCatalog.codes(scope)) {
            if (!referenced.contains(v.code()) && !packageHidden(v.code(), enabled, gated)) {
                out.add(new UnboundCode(v.code(), v.name(), v.description()));
            }
        }
        return out;
    }

    // ===== 公共：建树 =====

    /** 取 scope 下全部启用节点，按 sort 升序，分组到 {@code parentKey -> 子节点列表}（顶层 key=null）。 */
    private Map<String, List<SysMenu>> loadByParent(int scope) {
        List<SysMenu> rows = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getScope, scope)
                .eq(SysMenu::getStatus, STATUS_ENABLED)
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getNodeKey));
        Map<String, List<SysMenu>> byParent = new LinkedHashMap<>();
        for (SysMenu m : rows) {
            byParent.computeIfAbsent(m.getParentKey(), k -> new ArrayList<>()).add(m);
        }
        return byParent;
    }

    private static List<SysMenu> childrenOf(Map<String, List<SysMenu>> byParent, String parentKey) {
        return byParent.getOrDefault(parentKey, List.of());
    }
}
