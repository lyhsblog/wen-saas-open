package com.wensaas.core.impl.spi.module;

import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.impl.dict.DictSpiSynchronizer;
import com.wensaas.core.impl.notification.NotificationSpiSynchronizer;
import com.wensaas.core.impl.pkg.ModuleApiRouteRegistry;
import com.wensaas.core.impl.pkg.PackageSpiSynchronizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;
import com.wensaas.core.impl.spi.registry.DashboardCardRegistry;
import com.wensaas.core.impl.spi.registry.DataPermissionTableRegistry;
import com.wensaas.core.impl.spi.registry.DictRegistry;
import com.wensaas.core.impl.spi.registry.EventSubscriptionRegistry;
import com.wensaas.core.impl.spi.registry.H5PageRegistry;
import com.wensaas.core.impl.spi.registry.ImportEntityRegistry;
import com.wensaas.core.impl.spi.registry.NotificationTemplateRegistry;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import com.wensaas.core.impl.spi.registry.TaskHandlerRegistry;

/**
 * 业务模块启动期注册器（v2）。
 *
 * <p>流程：
 * <ol>
 *   <li>收集所有 {@link BusinessModuleDescriptor} Bean，按 code 字典序稳定排序</li>
 *   <li>按 {@link BusinessModulesProperties} 过滤 disabled</li>
 *   <li>{@link NamespaceValidator} 命名空间冲突一次性校验</li>
 *   <li>{@link DependencyTopologySorter} 拓扑排序 + 循环依赖 / 缺失依赖校验</li>
 *   <li>{@link ModuleTableValidator} 表归属 + DB 存在性校验（扫描 MyBatis-Plus entity）</li>
 *   <li>按拓扑序逐个注册到各内存 Registry</li>
 *   <li>{@link BusinessModuleDbSynchronizer} 同步 {@code sys_business_module} 表</li>
 *   <li>{@link PackageSpiSynchronizer} 同步 {@code sys_package} / {@code sys_package_module}
 *       （在 sys_business_module 落库之后，保证 module_code 语义完整）</li>
 *   <li>{@link DictSpiSynchronizer} 同步 {@code sys_dict_group} / {@code sys_dict_item}（如装配了字典框架）</li>
 * </ol>
 */
@Component
public class BusinessModuleRegistrar implements SmartInitializingSingleton, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(BusinessModuleRegistrar.class);

    private final BusinessModulesProperties properties;
    private final NamespaceValidator namespaceValidator;
    private final DependencyTopologySorter topologySorter;
    private final ModuleSchemaMigrator schemaMigrator;
    private final ModuleTableValidator tableValidator;
    private final BusinessModuleDbSynchronizer dbSynchronizer;
    private final BusinessModuleRegistry moduleRegistry;
    private final PermissionRegistry permissionRegistry;
    private final DictRegistry dictRegistry;
    private final DashboardCardRegistry dashboardCardRegistry;
    private final H5PageRegistry h5PageRegistry;
    private final NotificationTemplateRegistry notificationTemplateRegistry;
    private final ImportEntityRegistry importEntityRegistry;
    private final EventSubscriptionRegistry eventSubscriptionRegistry;
    private final CustomFieldObjectRegistry customFieldObjectRegistry;
    private final DataPermissionTableRegistry dataPermissionTableRegistry;
    private final TaskHandlerRegistry taskHandlerRegistry;
    private final ModuleApiRouteRegistry apiRouteRegistry;
    private final ObjectProvider<PackageSpiSynchronizer> packageSynchronizerProvider;
    private final ObjectProvider<DictSpiSynchronizer> dictSynchronizerProvider;
    private final ObjectProvider<NotificationSpiSynchronizer> notificationSynchronizerProvider;

    private ApplicationContext applicationContext;

    public BusinessModuleRegistrar(BusinessModulesProperties properties,
                                    NamespaceValidator namespaceValidator,
                                    DependencyTopologySorter topologySorter,
                                    ModuleSchemaMigrator schemaMigrator,
                                    ModuleTableValidator tableValidator,
                                    BusinessModuleDbSynchronizer dbSynchronizer,
                                    BusinessModuleRegistry moduleRegistry,
                                    PermissionRegistry permissionRegistry,
                                    DictRegistry dictRegistry,
                                    DashboardCardRegistry dashboardCardRegistry,
                                    H5PageRegistry h5PageRegistry,
                                    NotificationTemplateRegistry notificationTemplateRegistry,
                                    ImportEntityRegistry importEntityRegistry,
                                    EventSubscriptionRegistry eventSubscriptionRegistry,
                                    CustomFieldObjectRegistry customFieldObjectRegistry,
                                    DataPermissionTableRegistry dataPermissionTableRegistry,
                                    TaskHandlerRegistry taskHandlerRegistry,
                                    ModuleApiRouteRegistry apiRouteRegistry,
                                    ObjectProvider<PackageSpiSynchronizer> packageSynchronizerProvider,
                                    ObjectProvider<DictSpiSynchronizer> dictSynchronizerProvider,
                                    ObjectProvider<NotificationSpiSynchronizer> notificationSynchronizerProvider) {
        this.properties = properties;
        this.namespaceValidator = namespaceValidator;
        this.topologySorter = topologySorter;
        this.schemaMigrator = schemaMigrator;
        this.tableValidator = tableValidator;
        this.dbSynchronizer = dbSynchronizer;
        this.moduleRegistry = moduleRegistry;
        this.permissionRegistry = permissionRegistry;
        this.dictRegistry = dictRegistry;
        this.dashboardCardRegistry = dashboardCardRegistry;
        this.h5PageRegistry = h5PageRegistry;
        this.notificationTemplateRegistry = notificationTemplateRegistry;
        this.importEntityRegistry = importEntityRegistry;
        this.eventSubscriptionRegistry = eventSubscriptionRegistry;
        this.customFieldObjectRegistry = customFieldObjectRegistry;
        this.dataPermissionTableRegistry = dataPermissionTableRegistry;
        this.taskHandlerRegistry = taskHandlerRegistry;
        this.apiRouteRegistry = apiRouteRegistry;
        this.packageSynchronizerProvider = packageSynchronizerProvider;
        this.dictSynchronizerProvider = dictSynchronizerProvider;
        this.notificationSynchronizerProvider = notificationSynchronizerProvider;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, BusinessModuleDescriptor> beans = applicationContext.getBeansOfType(BusinessModuleDescriptor.class);
        if (beans.isEmpty()) {
            log.info("[SPI] 未发现任何业务模块（BusinessModuleDescriptor Bean）");
            // 即使没有 enabled 模块，也跑一次表校验，捕获"DB 有 entity 但 SPI 没归属"的负路径
            tableValidator.validate(List.of());
            return;
        }

        List<BusinessModuleDescriptor> discovered = beans.values().stream()
                .sorted(Comparator.comparing(d -> d.metadata().code()))
                .toList();

        List<String> skipped = discovered.stream()
                .map(d -> d.metadata().code())
                .filter(code -> !properties.isModuleEnabled(code))
                .toList();

        List<BusinessModuleDescriptor> enabled = discovered.stream()
                .filter(d -> properties.isModuleEnabled(d.metadata().code()))
                .toList();

        namespaceValidator.validate(enabled);
        List<BusinessModuleDescriptor> sortedEnabled = topologySorter.sort(enabled);

        // 表存在性校验前，按拓扑序为各模块执行独立 Flyway 迁移（每模块独立 history 表）
        schemaMigrator.migrate(sortedEnabled.stream().map(d -> d.metadata().code()).toList());

        tableValidator.validate(sortedEnabled);

        for (BusinessModuleDescriptor d : sortedEnabled) {
            register(d);
        }

        dbSynchronizer.sync(sortedEnabled);

        PackageSpiSynchronizer pkgSync = packageSynchronizerProvider.getIfAvailable();
        if (pkgSync != null) {
            pkgSync.sync(sortedEnabled);
        }

        DictSpiSynchronizer dictSync = dictSynchronizerProvider.getIfAvailable();
        if (dictSync != null) {
            dictSync.sync(sortedEnabled);
        }

        NotificationSpiSynchronizer notifySync = notificationSynchronizerProvider.getIfAvailable();
        if (notifySync != null) {
            notifySync.sync(sortedEnabled);
        }

        log.info("[SPI] 业务模块注册完成: 已注册 {} 个 {}{}",
                sortedEnabled.size(),
                sortedEnabled.stream().map(d -> d.metadata().code()).toList(),
                skipped.isEmpty() ? "" : "，跳过 " + skipped);
    }

    private void register(BusinessModuleDescriptor d) {
        String moduleCode = d.metadata().code();
        moduleRegistry.register(d.metadata());
        d.permissions().forEach(item -> permissionRegistry.register(moduleCode, item));
        d.dictGroups().forEach(item -> dictRegistry.register(moduleCode, item));
        d.dashboardCards().forEach(item -> dashboardCardRegistry.register(moduleCode, item));
        d.h5Pages().forEach(item -> h5PageRegistry.register(moduleCode, item));
        d.notificationTemplates().forEach(item -> notificationTemplateRegistry.register(moduleCode, item));
        d.importEntities().forEach(item -> importEntityRegistry.register(moduleCode, item));
        d.eventSubscriptions().forEach(item -> eventSubscriptionRegistry.register(moduleCode, item));
        d.customFieldObjects().forEach(item -> customFieldObjectRegistry.register(moduleCode, item));
        d.dataPermissionTables().forEach(item -> dataPermissionTableRegistry.register(moduleCode, item));
        d.taskHandlers().forEach(item -> taskHandlerRegistry.register(moduleCode, item));
        d.apiPathPrefixes().forEach(prefix -> apiRouteRegistry.register(prefix, moduleCode));
    }
}
