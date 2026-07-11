package com.wensaas.core.impl.permission.eval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.auth.permission.PermissionCatalogService;
import com.wensaas.core.api.auth.permission.PermissionCatalogService.CatalogMenu;
import com.wensaas.core.api.auth.permission.PermissionCatalogService.CatalogModule;
import com.wensaas.core.api.auth.permission.PermissionCatalogService.CatalogPoint;
import com.wensaas.core.api.auth.permission.RequirePermission;
import com.wensaas.core.impl.menu.SysMenu;
import com.wensaas.core.impl.menu.SysMenuMapper;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 启动期菜单 ↔ 权限码双向一致性校验。
 *
 * <p>权威码集合 = {@link PermissionRegistry}（SPI 注册）∪ {@link PermissionCatalogService}（底座静态目录）
 * ∪ 全部 {@link RequirePermission} 实际拦截码（含 watermark 这类裸拦截码）。
 *
 * <p>两个方向：
 * <ul>
 *   <li>菜单 {@code permission_code} ∉ 权威 → 置 {@code dangling=true} + ERROR（不阻断启动，便于进
 *       「菜单管理」修绑）；∈ 权威 → 纠正回 {@code dangling=false}（幂等）。</li>
 *   <li>权威码无任何菜单引用 → WARN（可能为页内自查的动作码，不阻断）。</li>
 * </ul>
 */
public class MenuPermissionConsistencyValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MenuPermissionConsistencyValidator.class);

    private final ListableBeanFactory beanFactory;
    private final PermissionRegistry permissionRegistry;
    private final PermissionCatalogService catalogService;
    private final SysMenuMapper menuMapper;

    public MenuPermissionConsistencyValidator(ListableBeanFactory beanFactory,
                                              PermissionRegistry permissionRegistry,
                                              PermissionCatalogService catalogService,
                                              SysMenuMapper menuMapper) {
        this.beanFactory = beanFactory;
        this.permissionRegistry = permissionRegistry;
        this.catalogService = catalogService;
        this.menuMapper = menuMapper;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<String> authoritative = collectAuthoritativeCodes();

        List<SysMenu> bound = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().isNotNull(SysMenu::getPermissionCode));
        Set<String> referenced = new HashSet<>();
        List<String> dangling = new ArrayList<>();
        for (SysMenu m : bound) {
            String code = m.getPermissionCode();
            referenced.add(code);
            boolean isDangling = !authoritative.contains(code);
            if (isDangling != Boolean.TRUE.equals(m.getDangling())) {
                SysMenu patch = new SysMenu();
                patch.setId(m.getId());
                patch.setDangling(isDangling);
                menuMapper.updateById(patch);
            }
            if (isDangling) {
                dangling.add(m.getScope() + "/" + m.getNodeKey() + " → " + code);
            }
        }
        if (!dangling.isEmpty()) {
            log.error("[menu] {} 个菜单节点绑定了未声明的权限码（已置 dangling，请在「菜单管理」修绑）: {}",
                    dangling.size(), dangling);
        }

        Set<String> unreferenced = new TreeSet<>(authoritative);
        unreferenced.removeAll(referenced);
        if (!unreferenced.isEmpty()) {
            log.warn("[menu] {} 个已声明权限码无任何菜单引用（可能为页内自查动作码）: {}",
                    unreferenced.size(), unreferenced);
        }
    }

    private Set<String> collectAuthoritativeCodes() {
        Set<String> codes = new HashSet<>();
        permissionRegistry.getAll().forEach(d -> {
            if (d.code() != null) {
                codes.add(d.code());
            }
        });
        for (CatalogModule mod : catalogService.listCatalog()) {
            for (CatalogMenu menu : mod.menus()) {
                for (CatalogPoint p : menu.points()) {
                    if (p.code() != null) {
                        codes.add(p.code());
                    }
                }
            }
        }
        codes.addAll(scanRequirePermissionCodes());
        return codes;
    }

    private Set<String> scanRequirePermissionCodes() {
        Set<String> codes = new HashSet<>();
        for (String name : beanFactory.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = beanFactory.getBean(name);
            } catch (RuntimeException ignored) {
                continue;
            }
            Class<?> target = AopUtils.getTargetClass(bean);
            collectFrom(AnnotatedElementUtils.findMergedAnnotation(target, RequirePermission.class), codes);
            for (Method m : target.getMethods()) {
                collectFrom(AnnotatedElementUtils.findMergedAnnotation(m, RequirePermission.class), codes);
            }
        }
        return codes;
    }

    private static void collectFrom(RequirePermission a, Set<String> codes) {
        if (a == null) {
            return;
        }
        if (!a.value().isEmpty()) {
            codes.add(a.value());
        }
        codes.addAll(Arrays.asList(a.anyOf()));
    }
}
