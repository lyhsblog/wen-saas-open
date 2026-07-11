package com.wensaas.core.impl.menu;

import com.wensaas.core.api.auth.permission.PermissionCatalogService;
import com.wensaas.core.api.auth.permission.PermissionCatalogService.CatalogMenu;
import com.wensaas.core.api.auth.permission.PermissionCatalogService.CatalogModule;
import com.wensaas.core.api.auth.permission.PermissionCatalogService.CatalogPoint;
import com.wensaas.core.api.menu.MenuQueryService;
import com.wensaas.core.api.menu.PermissionCodeCatalogService;
import com.wensaas.core.impl.permission.datascope.DataPermissionActionRegistry;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link PermissionCodeCatalogService} 默认实现。
 *
 * <p>OPS：{@link PermissionRegistry} 中 {@code ops.*} 码；TENANT：{@link PermissionCatalogService}
 * 功能权限目录（底座静态 + 业务模块 SPI）去 {@code ops.*}。{@code dataScoped} 取自
 * {@link DataPermissionActionRegistry}。
 */
public class PermissionCodeCatalogServiceImpl implements PermissionCodeCatalogService {

    private static final String OPS_PERM_PREFIX = "ops.";

    private final PermissionRegistry permissionRegistry;
    private final PermissionCatalogService catalogService;
    private final DataPermissionActionRegistry dataPermissionActionRegistry;

    public PermissionCodeCatalogServiceImpl(PermissionRegistry permissionRegistry,
                                            PermissionCatalogService catalogService,
                                            DataPermissionActionRegistry dataPermissionActionRegistry) {
        this.permissionRegistry = permissionRegistry;
        this.catalogService = catalogService;
        this.dataPermissionActionRegistry = dataPermissionActionRegistry;
    }

    @Override
    public List<PermissionCodeView> codes(int scope) {
        Set<String> dataScoped = Set.copyOf(dataPermissionActionRegistry.getActionCodes());
        Map<String, PermissionCodeView> out = new LinkedHashMap<>();
        if (scope == MenuQueryService.SCOPE_OPS) {
            permissionRegistry.getAll().stream()
                    .filter(d -> d.code() != null && d.code().startsWith(OPS_PERM_PREFIX))
                    .forEach(d -> out.putIfAbsent(d.code(), new PermissionCodeView(
                            d.code(), d.name(), d.description(), scope, dataScoped.contains(d.code()))));
        } else if (scope == MenuQueryService.SCOPE_TENANT) {
            for (CatalogModule mod : catalogService.listCatalog()) {
                for (CatalogMenu menu : mod.menus()) {
                    for (CatalogPoint p : menu.points()) {
                        if (p.code() != null && !p.code().startsWith(OPS_PERM_PREFIX)) {
                            out.putIfAbsent(p.code(), new PermissionCodeView(
                                    p.code(), p.name(), p.description(), scope, dataScoped.contains(p.code())));
                        }
                    }
                }
            }
        }
        return new ArrayList<>(out.values());
    }
}
