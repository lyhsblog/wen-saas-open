package com.wensaas.core.impl.menu;

import com.wensaas.core.api.menu.MenuQueryService;
import com.wensaas.core.api.menu.MenuQueryService.CatalogNode;
import com.wensaas.core.api.menu.MenuQueryService.CatalogTree;
import com.wensaas.core.api.menu.MenuQueryService.NavNode;
import com.wensaas.core.api.menu.MenuQueryService.UnboundCode;
import com.wensaas.core.api.menu.PermissionCodeCatalogService;
import com.wensaas.core.api.menu.PermissionCodeCatalogService.PermissionCodeView;
import com.wensaas.core.impl.pkg.PackageModuleGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MenuQueryServiceImpl} 单测：从内存伪造的 sys_menu 行验证 navTree 可见性裁剪与 catalogTree 投影。
 */
class MenuQueryServiceImplTest {

    private static final int OPS = MenuQueryService.SCOPE_OPS;

    private SysMenuMapper menuMapper;
    private PermissionCodeCatalogService codeCatalog;
    private PackageModuleGate packageGate;
    private MenuQueryServiceImpl service;

    @BeforeEach
    void setup() {
        menuMapper = mock(SysMenuMapper.class);
        codeCatalog = mock(PermissionCodeCatalogService.class);
        packageGate = mock(PackageModuleGate.class);
        // 默认无受控模块、任何码都不门控 → 既有断言不受影响；门控专项测试各自重设
        when(packageGate.gatedModuleCodes()).thenReturn(Set.of());
        when(packageGate.hidden(anyString(), any(), any())).thenReturn(false);
        service = new MenuQueryServiceImpl(menuMapper, codeCatalog, packageGate);

        // sys_menu（scope=OPS）伪造树：
        //   g1「系统」
        //     ├─ p_members「成员管理」(/sys/members, ops.member.list) ── a_create「新建」(ops.member.create)
        //     └─ p_logs「日志」(/sys/logs, ops.log.list)              ── 无动作
        //   g_empty「空组」
        //     └─ p_secret「机密」(/secret, ops.secret.view)
        //   p_profile「个人中心」(/profile, 无码)
        List<SysMenu> rows = List.of(
                group("g1", null, "系统", 10),
                page("p_members", "g1", "成员管理", "/sys/members", "ops.member.list", 10),
                action("a_create", "p_members", "新建", "ops.member.create", 10),
                page("p_logs", "g1", "日志", "/sys/logs", "ops.log.list", 20),
                group("g_empty", null, "空组", 20),
                page("p_secret", "g_empty", "机密", "/secret", "ops.secret.view", 10),
                page("p_profile", null, "个人中心", "/profile", null, 99));
        when(menuMapper.selectList(any())).thenReturn(new ArrayList<>(rows));

        when(codeCatalog.codes(OPS)).thenReturn(List.of(
                new PermissionCodeView("ops.member.list", "查看成员", null, OPS, false),
                new PermissionCodeView("ops.member.create", "新建成员", null, OPS, false),
                new PermissionCodeView("ops.log.list", "查看日志", null, OPS, false),
                new PermissionCodeView("ops.secret.view", "机密", null, OPS, false),
                new PermissionCodeView("ops.extra.view", "孤儿能力", "无菜单引用", OPS, false)));
    }

    @Test
    void navTree_filtersByPermission_prunesEmptyGroups_excludesActions() {
        List<NavNode> nav = service.navTree(OPS, Set.of("ops.member.list", "ops.member.create"), false, Set.of());

        // 顶层：g1（有可见子）+ p_profile（无码恒可见）；g_empty 因子页 ops.secret.view 未持有被剪
        assertThat(nav).extracting(NavNode::key).containsExactly("g1", "p_profile");
        NavNode g1 = nav.get(0);
        // p_logs 不可见（未持 ops.log.list）；a_create 是动作不进导航
        assertThat(g1.children()).extracting(NavNode::key).containsExactly("p_members");
        assertThat(g1.children().get(0).children()).isEmpty();
        assertThat(g1.children().get(0).path()).isEqualTo("/sys/members");
    }

    @Test
    void navTree_owner_seesEverything() {
        List<NavNode> nav = service.navTree(OPS, Set.of(), true, Set.of());

        assertThat(nav).extracting(NavNode::key).containsExactly("g1", "g_empty", "p_profile");
        assertThat(nav.get(0).children()).extracting(NavNode::key).containsExactly("p_members", "p_logs");
    }

    @Test
    void catalogTree_projectsContainersAndLeaves_andReportsUnbound() {
        CatalogTree catalog = service.catalogTree(OPS, Set.of());

        // 顶层容器：g1 + g_empty；p_profile（无码无动作）不进树
        assertThat(catalog.tree()).extracting(CatalogNode::key).containsExactly("g1", "g_empty");

        CatalogNode g1 = catalog.tree().get(0);
        assertThat(g1.code()).isNull(); // 容器不带码
        // 有子动作的 PAGE = 容器；无动作带码的 PAGE = 叶子
        assertThat(g1.children()).extracting(CatalogNode::key).containsExactly("p_members", "p_logs");

        CatalogNode members = g1.children().get(0);
        assertThat(members.code()).isNull();
        // gate 码投成「查看」叶子（key=node_key+".view"）+ 动作叶子
        assertThat(members.children()).extracting(CatalogNode::key)
                .containsExactly("p_members.view", "a_create");
        assertThat(members.children().get(0).label()).isEqualTo("查看");
        assertThat(members.children().get(0).code()).isEqualTo("ops.member.list");
        assertThat(members.children().get(1).code()).isEqualTo("ops.member.create");

        CatalogNode logs = g1.children().get(1);
        assertThat(logs.children()).isNull();
        assertThat(logs.code()).isEqualTo("ops.log.list");

        // unbound = 后端可授 ops 码 − 菜单已引用码 = {ops.extra.view}
        assertThat(catalog.unbound()).extracting(UnboundCode::code).containsExactly("ops.extra.view");
    }

    // ===== 套餐门控（S9）=====

    /**
     * 受控业务模块（声明非空 API 前缀）未开通时，其菜单/角色目录码整段隐去，连主管理员/unbound 也不可见；
     * 开通后恢复。底座码（无属主映射）不受影响。
     */
    @Test
    void packageGating_hidesUnpurchasedModule_inNavCatalogAndUnbound_restoredWhenEnabled() {
        // 在既有 OPS 树上叠一个受控模块页：g1 下 p_crm「客户」(crm.customer.list)
        List<SysMenu> rows = new ArrayList<>(List.of(
                group("g1", null, "系统", 10),
                page("p_members", "g1", "成员管理", "/sys/members", "ops.member.list", 10),
                page("p_crm", "g1", "客户", "/crm/customers", "crm.customer.list", 30)));
        when(menuMapper.selectList(any())).thenReturn(rows);
        when(codeCatalog.codes(OPS)).thenReturn(List.of(
                new PermissionCodeView("ops.member.list", "查看成员", null, OPS, false),
                new PermissionCodeView("crm.customer.list", "查看客户", null, OPS, false),
                new PermissionCodeView("crm.extra.view", "孤儿客户码", null, OPS, false)));
        when(packageGate.gatedModuleCodes()).thenReturn(Set.of("crm"));
        // crm.* 属 crm 模块：enabled 不含 "crm" 时隐藏；其余码（ops.*）永不隐藏（沿用 setup 默认 false）
        when(packageGate.hidden(eq("crm.customer.list"), any(), any()))
                .thenAnswer(inv -> !((Set<?>) inv.getArgument(1)).contains("crm"));
        when(packageGate.hidden(eq("crm.extra.view"), any(), any()))
                .thenAnswer(inv -> !((Set<?>) inv.getArgument(1)).contains("crm"));

        // 未开通 crm：owner 主管理员也看不到 p_crm；catalog 不含 crm 容器；unbound 不含 crm.extra.view
        List<NavNode> navOff = service.navTree(OPS, Set.of(), true, Set.of());
        assertThat(navOff.get(0).children()).extracting(NavNode::key).containsExactly("p_members");
        CatalogTree catOff = service.catalogTree(OPS, Set.of());
        assertThat(catOff.tree().get(0).children()).extracting(CatalogNode::key).containsExactly("p_members");
        // crm.extra.view 是 crm 模块孤儿码，未购时不进 unbound（ops.member.list 已被菜单引用、本就不在 unbound）
        assertThat(catOff.unbound()).extracting(UnboundCode::code).doesNotContain("crm.extra.view");

        // 开通 crm：p_crm 重新出现在导航与角色目录
        List<NavNode> navOn = service.navTree(OPS, Set.of(), true, Set.of("crm"));
        assertThat(navOn.get(0).children()).extracting(NavNode::key).containsExactly("p_members", "p_crm");
        CatalogTree catOn = service.catalogTree(OPS, Set.of("crm"));
        assertThat(catOn.tree().get(0).children()).extracting(CatalogNode::key).contains("p_crm");
        assertThat(catOn.unbound()).extracting(UnboundCode::code).contains("crm.extra.view");
    }

    // ===== helpers =====

    private static SysMenu group(String key, String parent, String label, int sort) {
        return row(key, parent, 1, label, null, null, sort);
    }

    private static SysMenu page(String key, String parent, String label, String path, String code, int sort) {
        return row(key, parent, 2, label, path, code, sort);
    }

    private static SysMenu action(String key, String parent, String label, String code, int sort) {
        return row(key, parent, 3, label, null, code, sort);
    }

    private static SysMenu row(String key, String parent, int type, String label,
                               String path, String code, int sort) {
        SysMenu m = new SysMenu();
        m.setScope(1);
        m.setNodeKey(key);
        m.setParentKey(parent);
        m.setNodeType(type);
        m.setLabel(label);
        m.setPath(path);
        m.setPermissionCode(code);
        m.setSort(sort);
        m.setStatus(1);
        return m;
    }
}
