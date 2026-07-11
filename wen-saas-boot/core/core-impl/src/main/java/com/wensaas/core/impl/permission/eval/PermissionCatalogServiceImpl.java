package com.wensaas.core.impl.permission.eval;

import com.wensaas.core.api.auth.permission.PermissionCatalogService;
import com.wensaas.core.api.spi.def.PermissionDef;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.wensaas.core.impl.permission.datascope.DataPermissionActionRegistry;

/**
 * 功能权限目录实现。
 *
 * <p>权限码代码权威统一为 {@link PermissionRegistry}：底座租户码由 {@code BasePermissionContributor}
 * 注册、业务模块码由 {@code BusinessModuleDescriptor.permissions()} 注册。本服务从 registry 派生
 * 扁平目录（按归属模块分组），仅用于内部消费（权限码集合 / 名称解析 / 数据权限动作名）；
 * 角色页的「模块→菜单→动作」展示树以 {@code sys_menu} 为权威（{@code MenuQueryService.catalogTree}），不走此处。
 * 排除 {@code ops.*}（运营域，OPS scope 单列）。
 */
public class PermissionCatalogServiceImpl implements PermissionCatalogService {

    private static final String OPS_PERM_PREFIX = "ops.";

    private final PermissionRegistry permissionRegistry;
    private final DataPermissionActionRegistry dataPermissionActionRegistry;

    public PermissionCatalogServiceImpl(PermissionRegistry permissionRegistry,
                                         DataPermissionActionRegistry dataPermissionActionRegistry) {
        this.permissionRegistry = permissionRegistry;
        this.dataPermissionActionRegistry = dataPermissionActionRegistry;
    }

    @Override
    public List<DataPermissionActionDef> listDataPermissionActions() {
        // 名称用完整目录（底座 + 业务模块）解析，覆盖底座功能点（如 audit.view）；查不到回退裸 code。
        Map<String, String> nameByCode = new LinkedHashMap<>();
        for (CatalogModule m : listCatalog()) {
            for (CatalogMenu menu : m.menus()) {
                for (CatalogPoint p : menu.points()) {
                    nameByCode.put(p.code(), p.name());
                }
            }
        }
        List<DataPermissionActionDef> out = new ArrayList<>();
        for (String code : dataPermissionActionRegistry.getActionCodes()) {
            out.add(new DataPermissionActionDef(code, nameByCode.getOrDefault(code, code)));
        }
        return out;
    }

    @Override
    public List<CatalogModule> listCatalog() {
        return registryCatalog();
    }

    /** 从 PermissionRegistry 派生扁平目录：按归属模块分组、菜单按 code 第二段切分；排除 ops.*。 */
    private List<CatalogModule> registryCatalog() {
        // moduleCode -> (menuKey -> points)
        Map<String, Map<String, List<CatalogPoint>>> byModule = new LinkedHashMap<>();
        permissionRegistry.getAll().stream()
                .filter(d -> d.code() != null
                        && !d.code().startsWith(OPS_PERM_PREFIX))
                .sorted(Comparator.comparing(PermissionDef::code))
                .forEach(d -> {
                    String module = permissionRegistry.findOwnerModule(d.code()).orElse(firstSegment(d.code()));
                    String menuKey = menuKeyOf(d.code());
                    byModule.computeIfAbsent(module, k -> new LinkedHashMap<>())
                            .computeIfAbsent(menuKey, k -> new ArrayList<>())
                            .add(new CatalogPoint(d.code(), d.name(), d.description()));
                });

        List<CatalogModule> modules = new ArrayList<>(byModule.size());
        byModule.forEach((module, menus) -> {
            List<CatalogMenu> menuList = new ArrayList<>(menus.size());
            menus.forEach((menuKey, points) -> menuList.add(new CatalogMenu(menuKey, menuKey, points)));
            modules.add(new CatalogModule(module, module, false, menuList));
        });
        return modules;
    }

    private static String firstSegment(String code) {
        int i = code.indexOf('.');
        return i < 0 ? code : code.substring(0, i);
    }

    /** 菜单 key = code 去掉最后一段（操作）。如 {@code crm.customer.list -> crm.customer}；两段则取首段。 */
    private static String menuKeyOf(String code) {
        int last = code.lastIndexOf('.');
        if (last < 0) return code;
        String head = code.substring(0, last);
        return head.isEmpty() ? code : head;
    }
}
