package com.wensaas.core.impl.menu;

import com.wensaas.core.api.menu.MenuAdminException;
import com.wensaas.core.api.menu.MenuAdminService.CreateMenuCommand;
import com.wensaas.core.api.menu.MenuAdminService.MenuAdminNode;
import com.wensaas.core.api.menu.PermissionCodeCatalogService;
import com.wensaas.core.api.menu.PermissionCodeCatalogService.PermissionCodeView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MenuAdminServiceImpl} 单测：节点形状 / 父节点 / 权限码 / 删除规则校验。
 */
class MenuAdminServiceImplTest {

    private static final int OPS = 1;

    private SysMenuMapper menuMapper;
    private PermissionCodeCatalogService codeCatalog;
    private MenuAdminServiceImpl service;

    @BeforeEach
    void setup() {
        menuMapper = mock(SysMenuMapper.class);
        codeCatalog = mock(PermissionCodeCatalogService.class);
        service = new MenuAdminServiceImpl(menuMapper, codeCatalog);
        when(codeCatalog.codes(OPS)).thenReturn(List.of(
                new PermissionCodeView("ops.health.view", "客户健康度", null, OPS, false)));
    }

    @Test
    void create_group_withPath_rejected() {
        assertThatThrownBy(() -> service.create(new CreateMenuCommand(
                OPS, null, 1, "分组", null, "/x", null, null, 0)))
                .isInstanceOf(MenuAdminException.class)
                .extracting("code").hasToString("INVALID_NODE_SHAPE");
        verify(menuMapper, never()).insert(any(SysMenu.class));
    }

    @Test
    void create_page_withoutPath_rejected() {
        assertThatThrownBy(() -> service.create(new CreateMenuCommand(
                OPS, null, 2, "页面", null, null, "ops.health.view", null, 0)))
                .isInstanceOf(MenuAdminException.class)
                .extracting("code").hasToString("INVALID_NODE_SHAPE");
    }

    @Test
    void create_page_withUnknownCode_rejected() {
        assertThatThrownBy(() -> service.create(new CreateMenuCommand(
                OPS, null, 2, "页面", null, "/x", "ops.bogus", null, 0)))
                .isInstanceOf(MenuAdminException.class)
                .extracting("code").hasToString("UNKNOWN_PERMISSION_CODE");
    }

    @Test
    void create_action_atTopLevel_rejected() {
        assertThatThrownBy(() -> service.create(new CreateMenuCommand(
                OPS, null, 3, "动作", null, null, "ops.health.view", null, 0)))
                .isInstanceOf(MenuAdminException.class)
                .extracting("code").hasToString("INVALID_PARENT");
    }

    @Test
    void create_topLevelPage_ok_generatesAdminKey() {
        when(menuMapper.insert(any(SysMenu.class))).thenReturn(1);
        MenuAdminNode node = service.create(new CreateMenuCommand(
                OPS, null, 2, "健康度", "line-chart", "/health", "ops.health.view", null, 5));
        assertThat(node.nodeKey()).startsWith("adm_");
        assertThat(node.source()).isEqualTo(2); // ADMIN
        assertThat(node.permissionCode()).isEqualTo("ops.health.view");
        verify(menuMapper).insert(any(SysMenu.class));
    }

    @Test
    void delete_seedNode_rejected() {
        SysMenu seed = new SysMenu();
        seed.setId(910001L);
        seed.setScope(OPS);
        seed.setNodeKey("ops.workbench");
        seed.setSource(1); // SEED
        when(menuMapper.selectById(910001L)).thenReturn(seed);

        assertThatThrownBy(() -> service.delete(910001L))
                .isInstanceOf(MenuAdminException.class)
                .extracting("code").hasToString("CANNOT_DELETE_SEED");
        verify(menuMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void delete_adminNode_withChildren_rejected() {
        SysMenu adm = new SysMenu();
        adm.setId(700L);
        adm.setScope(OPS);
        adm.setNodeKey("adm_700");
        adm.setSource(2); // ADMIN
        when(menuMapper.selectById(700L)).thenReturn(adm);
        when(menuMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(700L))
                .isInstanceOf(MenuAdminException.class)
                .extracting("code").hasToString("MENU_HAS_CHILDREN");
        verify(menuMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void delete_adminNode_noChildren_ok() {
        SysMenu adm = new SysMenu();
        adm.setId(701L);
        adm.setScope(OPS);
        adm.setNodeKey("adm_701");
        adm.setSource(2);
        when(menuMapper.selectById(701L)).thenReturn(adm);
        when(menuMapper.selectCount(any())).thenReturn(0L);

        service.delete(701L);
        verify(menuMapper).deleteById(701L);
    }
}
