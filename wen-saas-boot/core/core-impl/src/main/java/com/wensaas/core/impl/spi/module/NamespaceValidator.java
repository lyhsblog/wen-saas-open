package com.wensaas.core.impl.spi.module;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.def.DashboardCardDef;
import com.wensaas.core.api.spi.def.DictGroupDef;
import com.wensaas.core.api.spi.def.H5PageDef;
import com.wensaas.core.api.spi.def.NotificationTemplateDef;
import com.wensaas.core.api.spi.def.PermissionDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 业务模块命名空间冲突校验器。
 *
 * <p>校验范围：模块编码 / 菜单路径 / 权限点编码 / 字典编码 /
 * H5 路由 / 通知模板编码 / 工作台卡片编码。一次性收集所有冲突，统一抛 {@link BusinessModuleConflictException}。
 */
@Component
public class NamespaceValidator {

    public void validate(List<BusinessModuleDescriptor> modules) {
        List<String> conflicts = new ArrayList<>();

        // 模块编码：全局唯一
        Map<String, String> seenModules = new LinkedHashMap<>();
        for (BusinessModuleDescriptor m : modules) {
            String code = m.metadata().code();
            String prev = seenModules.put(code, code);
            if (prev != null) {
                conflicts.add("模块编码冲突: '" + code + "' 被多次声明");
            }
        }

        // 每类 key 既要全局唯一（checkUnique），又要落在 owner 模块自己的命名空间内（checkPrefix，架构审查 M12）。
        // 命名约定：权限点/字典/通知/卡片用 '{code}.'，H5 用 '{code}/'。
        // 菜单不再由 SPI 声明（菜单树以 sys_menu 为唯一权威），故此处无菜单命名空间校验。
        checkUnique(modules, BusinessModuleDescriptor::permissions, PermissionDef::code, "权限点编码", conflicts);
        checkPrefix(modules, BusinessModuleDescriptor::permissions, PermissionDef::code, "权限点编码", conflicts);
        checkUnique(modules, BusinessModuleDescriptor::dictGroups, DictGroupDef::code, "字典编码", conflicts);
        checkPrefix(modules, BusinessModuleDescriptor::dictGroups, DictGroupDef::code, "字典编码", conflicts);
        checkUnique(modules, BusinessModuleDescriptor::h5Pages, H5PageDef::route, "H5 路由", conflicts);
        checkPrefix(modules, BusinessModuleDescriptor::h5Pages, H5PageDef::route, "H5 路由", conflicts);
        checkUnique(modules, BusinessModuleDescriptor::notificationTemplates, NotificationTemplateDef::code, "通知模板编码", conflicts);
        checkPrefix(modules, BusinessModuleDescriptor::notificationTemplates, NotificationTemplateDef::code, "通知模板编码", conflicts);
        checkUnique(modules, BusinessModuleDescriptor::dashboardCards, DashboardCardDef::code, "工作台卡片编码", conflicts);
        checkPrefix(modules, BusinessModuleDescriptor::dashboardCards, DashboardCardDef::code, "工作台卡片编码", conflicts);

        if (!conflicts.isEmpty()) {
            throw new BusinessModuleConflictException(
                    "业务模块命名空间冲突，启动阻断:\n  - " + String.join("\n  - ", conflicts));
        }
    }

    private <T> void checkUnique(List<BusinessModuleDescriptor> modules,
                                  Function<BusinessModuleDescriptor, List<T>> extractor,
                                  Function<T, String> keyFn,
                                  String label,
                                  List<String> conflicts) {
        Map<String, String> keyToOwner = new LinkedHashMap<>();
        for (BusinessModuleDescriptor m : modules) {
            String owner = m.metadata().code();
            for (T def : extractor.apply(m)) {
                String key = keyFn.apply(def);
                String prev = keyToOwner.put(key, owner);
                if (prev != null) {
                    if (prev.equals(owner)) {
                        conflicts.add(label + "冲突: '" + key + "' 在模块 [" + owner + "] 内重复声明");
                    } else {
                        conflicts.add(label + "冲突: '" + key + "' 同时被模块 [" + prev + "] 和 [" + owner + "] 声明");
                    }
                }
            }
        }
    }

    /**
     * 命名空间前缀校验（架构审查 M12）：每个 key 必须落在 owner 模块自己的命名空间内，即以
     * {@code {code}} 开头且其后紧跟分隔符（'.' / '/' / '_' / '-'）或恰好等于 code。
     *
     * <p>与 {@link ModuleTableValidator} 的表前缀校验对齐——堵住「模块 A 声明属于模块 B 命名空间的
     * 权限点/菜单/字典」这类静默越界（原先只做全局唯一、不校验归属）。用「分隔符边界」而非裸 startsWith，
     * 避免 code 'crm' 误判 key 'crmother.x' 合法。
     */
    private <T> void checkPrefix(List<BusinessModuleDescriptor> modules,
                                 Function<BusinessModuleDescriptor, List<T>> extractor,
                                 Function<T, String> keyFn,
                                 String label,
                                 List<String> conflicts) {
        for (BusinessModuleDescriptor m : modules) {
            String owner = m.metadata().code();
            for (T def : extractor.apply(m)) {
                String key = keyFn.apply(def);
                if (!hasNamespacePrefix(key, owner)) {
                    conflicts.add(label + "命名空间越界: '" + key + "' 不属于模块 [" + owner
                            + "] 命名空间（应以 '" + owner + "' 前缀开头）");
                }
            }
        }
    }

    /** key 是否落在模块 code 命名空间内：等于 code，或以 code + 分隔符开头。 */
    static boolean hasNamespacePrefix(String key, String code) {
        if (key == null || !key.startsWith(code)) {
            return false;
        }
        if (key.length() == code.length()) {
            return true;
        }
        char sep = key.charAt(code.length());
        return sep == '.' || sep == '/' || sep == '_' || sep == '-';
    }
}
