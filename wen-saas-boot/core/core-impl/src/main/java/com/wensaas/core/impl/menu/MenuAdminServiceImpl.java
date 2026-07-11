package com.wensaas.core.impl.menu;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.menu.MenuAdminException;
import com.wensaas.core.api.menu.MenuAdminException.Code;
import com.wensaas.core.api.menu.MenuAdminService;
import com.wensaas.core.api.menu.PermissionCodeCatalogService;
import com.wensaas.core.api.menu.PermissionCodeCatalogService.PermissionCodeView;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link MenuAdminService} 默认实现。写 {@code sys_menu}。
 *
 * <p>保存即按节点形状（GROUP→无 path/code；PAGE→有 path、parent 为 GROUP 或顶层；ACTION→有 code、
 * 无 path、parent 必为 PAGE）与权限码目录校验；通过则置 {@code dangling=false}。ADMIN 节点可删（且须无子），
 * SEED 节点仅可改 / 启停。
 */
public class MenuAdminServiceImpl implements MenuAdminService {

    private static final int NODE_GROUP = 1;
    private static final int NODE_PAGE = 2;
    private static final int NODE_ACTION = 3;
    private static final int SOURCE_SEED = 1;
    private static final int SOURCE_ADMIN = 2;
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final SysMenuMapper menuMapper;
    private final PermissionCodeCatalogService codeCatalog;

    public MenuAdminServiceImpl(SysMenuMapper menuMapper, PermissionCodeCatalogService codeCatalog) {
        this.menuMapper = menuMapper;
        this.codeCatalog = codeCatalog;
    }

    @Override
    public List<MenuAdminNode> tree(int scope) {
        List<SysMenu> rows = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getScope, scope)
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getNodeKey));
        Map<String, List<SysMenu>> byParent = new LinkedHashMap<>();
        for (SysMenu m : rows) {
            byParent.computeIfAbsent(m.getParentKey(), k -> new ArrayList<>()).add(m);
        }
        return buildLevel(byParent, null);
    }

    private List<MenuAdminNode> buildLevel(Map<String, List<SysMenu>> byParent, String parentKey) {
        List<MenuAdminNode> out = new ArrayList<>();
        for (SysMenu m : byParent.getOrDefault(parentKey, List.of())) {
            out.add(toNode(m, buildLevel(byParent, m.getNodeKey())));
        }
        return out;
    }

    @Override
    public MenuAdminNode create(CreateMenuCommand cmd) {
        if (cmd.scope() == null || cmd.nodeType() == null) {
            throw new MenuAdminException(Code.INVALID_NODE_SHAPE, "scope 与 nodeType 必填");
        }
        if (cmd.label() == null || cmd.label().isBlank()) {
            throw new MenuAdminException(Code.INVALID_NODE_SHAPE, "label 不能为空");
        }
        int scope = cmd.scope();
        int type = cmd.nodeType();
        validateShape(type, cmd.path(), cmd.permissionCode());
        validateParent(scope, type, cmd.parentKey());
        validateCode(scope, cmd.permissionCode());

        SysMenu m = new SysMenu();
        m.setScope(scope);
        m.setNodeKey("adm_" + IdWorker.getId());
        m.setParentKey(cmd.parentKey());
        m.setNodeType(type);
        m.setLabel(cmd.label());
        m.setIcon(cmd.icon());
        m.setPath(cmd.path());
        m.setPermissionCode(cmd.permissionCode());
        m.setDescription(cmd.description());
        m.setSort(cmd.sort() == null ? 0 : cmd.sort());
        m.setSource(SOURCE_ADMIN);
        m.setStatus(STATUS_ENABLED);
        m.setDangling(false);
        LocalDateTime now = LocalDateTime.now();
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        menuMapper.insert(m);
        return toNode(m, List.of());
    }

    @Override
    public MenuAdminNode update(long id, UpdateMenuCommand cmd) {
        SysMenu m = requireById(id);
        String label = cmd.label() != null && !cmd.label().isBlank() ? cmd.label() : m.getLabel();
        String path = cmd.path();
        String code = cmd.permissionCode();
        validateShape(m.getNodeType(), path, code);
        validateCode(m.getScope(), code);

        m.setLabel(label);
        m.setIcon(cmd.icon());
        m.setPath(path);
        m.setPermissionCode(code);
        m.setDescription(cmd.description());
        if (cmd.sort() != null) {
            m.setSort(cmd.sort());
        }
        m.setDangling(false); // 已校验码合法
        m.setUpdatedAt(LocalDateTime.now());
        menuMapper.updateById(m);
        return toNode(m, List.of());
    }

    @Override
    public void move(long id, String parentKey, int sort) {
        SysMenu m = requireById(id);
        validateParent(m.getScope(), m.getNodeType(), parentKey);
        m.setParentKey(parentKey);
        m.setSort(sort);
        m.setUpdatedAt(LocalDateTime.now());
        menuMapper.updateById(m);
    }

    @Override
    public MenuAdminNode setStatus(long id, boolean enabled) {
        SysMenu m = requireById(id);
        m.setStatus(enabled ? STATUS_ENABLED : STATUS_DISABLED);
        m.setUpdatedAt(LocalDateTime.now());
        menuMapper.updateById(m);
        return toNode(m, List.of());
    }

    @Override
    public void delete(long id) {
        SysMenu m = requireById(id);
        if (m.getSource() != null && m.getSource() == SOURCE_SEED) {
            throw new MenuAdminException(Code.CANNOT_DELETE_SEED, "种子节点不可删除，只能停用");
        }
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getScope, m.getScope())
                .eq(SysMenu::getParentKey, m.getNodeKey()));
        if (childCount != null && childCount > 0) {
            throw new MenuAdminException(Code.MENU_HAS_CHILDREN, "节点仍有子节点，请先移除或删除子节点");
        }
        menuMapper.deleteById(id);
    }

    // ===== 校验 =====

    private void validateShape(int type, String path, String code) {
        switch (type) {
            case NODE_GROUP -> {
                if (notBlank(path) || notBlank(code)) {
                    throw new MenuAdminException(Code.INVALID_NODE_SHAPE, "分组节点不能有 path / 权限码");
                }
            }
            case NODE_PAGE -> {
                if (!notBlank(path)) {
                    throw new MenuAdminException(Code.INVALID_NODE_SHAPE, "页面节点必须有 path");
                }
            }
            case NODE_ACTION -> {
                if (!notBlank(code)) {
                    throw new MenuAdminException(Code.INVALID_NODE_SHAPE, "动作节点必须绑定权限码");
                }
                if (notBlank(path)) {
                    throw new MenuAdminException(Code.INVALID_NODE_SHAPE, "动作节点不能有 path");
                }
            }
            default -> throw new MenuAdminException(Code.INVALID_NODE_SHAPE, "未知 nodeType: " + type);
        }
    }

    /** parent 类型相容：GROUP/PAGE 的父为 null 或 GROUP；ACTION 的父必为 PAGE。 */
    private void validateParent(int scope, int type, String parentKey) {
        if (parentKey == null || parentKey.isBlank()) {
            if (type == NODE_ACTION) {
                throw new MenuAdminException(Code.INVALID_PARENT, "动作节点必须挂在某个页面下");
            }
            return;
        }
        SysMenu parent = findByKey(scope, parentKey);
        if (parent == null) {
            throw new MenuAdminException(Code.INVALID_PARENT, "父节点不存在: " + parentKey);
        }
        if (type == NODE_ACTION) {
            if (parent.getNodeType() != NODE_PAGE) {
                throw new MenuAdminException(Code.INVALID_PARENT, "动作节点的父必须是页面");
            }
        } else if (parent.getNodeType() != NODE_GROUP) {
            throw new MenuAdminException(Code.INVALID_PARENT, "分组 / 页面节点的父必须是分组");
        }
    }

    private void validateCode(int scope, String code) {
        if (!notBlank(code)) {
            return;
        }
        Set<String> known = codeCatalog.codes(scope).stream()
                .map(PermissionCodeView::code).collect(Collectors.toSet());
        if (!known.contains(code)) {
            throw new MenuAdminException(Code.UNKNOWN_PERMISSION_CODE, "未知权限码（不在后端目录中）: " + code);
        }
    }

    // ===== helpers =====

    private SysMenu requireById(long id) {
        SysMenu m = menuMapper.selectById(id);
        if (m == null) {
            throw new MenuAdminException(Code.MENU_NOT_FOUND, "菜单节点不存在: " + id);
        }
        return m;
    }

    private SysMenu findByKey(int scope, String nodeKey) {
        return menuMapper.selectOne(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getScope, scope)
                .eq(SysMenu::getNodeKey, nodeKey));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static MenuAdminNode toNode(SysMenu m, List<MenuAdminNode> children) {
        return new MenuAdminNode(
                String.valueOf(m.getId()),
                m.getScope(),
                m.getNodeKey(),
                m.getParentKey(),
                m.getNodeType(),
                m.getLabel(),
                m.getIcon(),
                m.getPath(),
                m.getPermissionCode(),
                m.getDescription(),
                m.getSort() == null ? 0 : m.getSort(),
                m.getSource() == null ? SOURCE_SEED : m.getSource(),
                m.getStatus() == null ? STATUS_ENABLED : m.getStatus(),
                Boolean.TRUE.equals(m.getDangling()),
                m.getUpdatedAt(),
                children);
    }
}
