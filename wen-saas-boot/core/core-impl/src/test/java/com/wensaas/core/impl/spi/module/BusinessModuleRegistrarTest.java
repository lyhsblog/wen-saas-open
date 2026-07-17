package com.wensaas.core.impl.spi.module;

import com.wensaas.core.api.spi.BusinessModuleConflictException;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
import com.wensaas.core.api.spi.def.PermissionDef;
import com.wensaas.core.impl.dict.DictSpiSynchronizer;
import com.wensaas.core.impl.pkg.ModuleApiRouteRegistry;
import com.wensaas.core.impl.pkg.PackageSpiSynchronizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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

class BusinessModuleRegistrarTest {

    private BusinessModulesProperties properties;
    private BusinessModuleRegistry moduleRegistry;
    private PermissionRegistry permissionRegistry;
    private ModuleTableValidator tableValidator;
    private BusinessModuleDbSynchronizer dbSynchronizer;
    private PackageSpiSynchronizer packageSynchronizer;
    private BusinessModuleRegistrar registrar;
    private ApplicationContext context;

    @BeforeEach
    void setUp() {
        properties = new BusinessModulesProperties();
        moduleRegistry = new BusinessModuleRegistry();
        permissionRegistry = new PermissionRegistry();
        tableValidator = mock(ModuleTableValidator.class);
        dbSynchronizer = mock(BusinessModuleDbSynchronizer.class);
        packageSynchronizer = mock(PackageSpiSynchronizer.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<PackageSpiSynchronizer> pkgSyncProvider = mock(ObjectProvider.class);
        when(pkgSyncProvider.getIfAvailable()).thenReturn(packageSynchronizer);
        @SuppressWarnings("unchecked")
        ObjectProvider<DictSpiSynchronizer> dictSyncProvider = mock(ObjectProvider.class);
        when(dictSyncProvider.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<com.wensaas.core.impl.notification.NotificationSpiSynchronizer> notifySyncProvider =
                mock(ObjectProvider.class);
        when(notifySyncProvider.getIfAvailable()).thenReturn(null);
        registrar = new BusinessModuleRegistrar(
                properties,
                new NamespaceValidator(),
                new DependencyTopologySorter(),
                mock(ModuleSchemaMigrator.class),
                tableValidator,
                dbSynchronizer,
                moduleRegistry,
                permissionRegistry,
                new DictRegistry(),
                new DashboardCardRegistry(),
                new H5PageRegistry(),
                new NotificationTemplateRegistry(),
                new ImportEntityRegistry(),
                new EventSubscriptionRegistry(),
                new CustomFieldObjectRegistry(),
                new DataPermissionTableRegistry(),
                new TaskHandlerRegistry(),
                new ModuleApiRouteRegistry(),
                pkgSyncProvider,
                dictSyncProvider,
                notifySyncProvider);
        context = mock(ApplicationContext.class);
        registrar.setApplicationContext(context);
    }

    @Test
    void noBusinessModules_logsAndReturns() {
        when(context.getBeansOfType(BusinessModuleDescriptor.class)).thenReturn(Map.of());
        registrar.afterSingletonsInstantiated();
        assertThat(moduleRegistry.size()).isZero();
        // 空模块场景也要跑一次校验（捕获"DB 有 entity 但无 SPI 模块归属"）
        verify(tableValidator).validate(List.of());
        verifyNoInteractions(dbSynchronizer);
    }

    @Test
    void twoModules_registeredAndIndexedByExtensionPoint() {
        var crm = stub("crm",
                List.of(PermissionDef.of("crm.customer.list", "查询")));
        var approval = stub("approval",
                List.of(PermissionDef.of("approval.task.list", "查询")));
        when(context.getBeansOfType(BusinessModuleDescriptor.class))
                .thenReturn(beans("crm", crm, "approval", approval));

        registrar.afterSingletonsInstantiated();

        assertThat(moduleRegistry.size()).isEqualTo(2);
        assertThat(moduleRegistry.find("crm")).isPresent();
        assertThat(moduleRegistry.find("approval")).isPresent();
        assertThat(permissionRegistry.size()).isEqualTo(2);
        verify(tableValidator).validate(anyList());
        // 套餐同步须在模块表落库之后（sys_package_module.module_code 语义完整）
        var inOrder = inOrder(dbSynchronizer, packageSynchronizer);
        inOrder.verify(dbSynchronizer).sync(anyList());
        inOrder.verify(packageSynchronizer).sync(anyList());
    }

    @Test
    void disabledModule_isSkipped() {
        properties.setEnabled(Map.of("approval", false));
        var crm = stub("crm", List.of());
        var approval = stub("approval", List.of());
        when(context.getBeansOfType(BusinessModuleDescriptor.class))
                .thenReturn(beans("crm", crm, "approval", approval));

        registrar.afterSingletonsInstantiated();

        assertThat(moduleRegistry.find("crm")).isPresent();
        assertThat(moduleRegistry.find("approval")).isEmpty();
        assertThat(moduleRegistry.size()).isEqualTo(1);
    }

    @Test
    void duplicateModuleCode_throwsConflict() {
        var a = stub("crm", List.of());
        var b = stub("crm", List.of());
        when(context.getBeansOfType(BusinessModuleDescriptor.class))
                .thenReturn(beans("a", a, "b", b));

        assertThatThrownBy(() -> registrar.afterSingletonsInstantiated())
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("模块编码冲突")
                .hasMessageContaining("crm");

        assertThat(moduleRegistry.size()).isZero();
        verifyNoInteractions(tableValidator, dbSynchronizer);
    }

    @Test
    void cyclicDependency_abortsBeforeRegister() {
        var a = stubWithDeps("a", List.of("b"));
        var b = stubWithDeps("b", List.of("a"));
        when(context.getBeansOfType(BusinessModuleDescriptor.class))
                .thenReturn(beans("a", a, "b", b));

        assertThatThrownBy(() -> registrar.afterSingletonsInstantiated())
                .isInstanceOf(BusinessModuleConflictException.class)
                .hasMessageContaining("循环依赖");

        assertThat(moduleRegistry.size()).isZero();
        verifyNoInteractions(tableValidator, dbSynchronizer);
    }

    @Test
    void registersInTopologicalOrder() {
        // c 依赖 b 依赖 a；输入乱序 → 注册顺序 a, b, c
        var a = stub("a", List.of());
        var b = stubWithDeps("b", List.of("a"));
        var c = stubWithDeps("c", List.of("b"));
        when(context.getBeansOfType(BusinessModuleDescriptor.class))
                .thenReturn(beans("c", c, "a", a, "b", b));

        registrar.afterSingletonsInstantiated();

        List<String> codes = moduleRegistry.getAll().stream().map(ModuleMetadata::code).toList();
        assertThat(codes).containsExactly("a", "b", "c");
    }

    private BusinessModuleDescriptor stub(String code, List<PermissionDef> perms) {
        return new BusinessModuleDescriptor() {
            @Override public ModuleMetadata metadata() { return ModuleMetadata.of(code, code, "1.0.0"); }
            @Override public List<PermissionDef> permissions() { return perms; }
        };
    }

    private BusinessModuleDescriptor stubWithDeps(String code, List<String> deps) {
        return new BusinessModuleDescriptor() {
            @Override public ModuleMetadata metadata() {
                return new ModuleMetadata(code, code, null, "1.0.0", null, deps);
            }
        };
    }

    private Map<String, BusinessModuleDescriptor> beans(String n1, BusinessModuleDescriptor b1,
                                                         String n2, BusinessModuleDescriptor b2) {
        Map<String, BusinessModuleDescriptor> m = new LinkedHashMap<>();
        m.put(n1, b1);
        m.put(n2, b2);
        return m;
    }

    private Map<String, BusinessModuleDescriptor> beans(String n1, BusinessModuleDescriptor b1,
                                                         String n2, BusinessModuleDescriptor b2,
                                                         String n3, BusinessModuleDescriptor b3) {
        Map<String, BusinessModuleDescriptor> m = new LinkedHashMap<>();
        m.put(n1, b1);
        m.put(n2, b2);
        m.put(n3, b3);
        return m;
    }
}
