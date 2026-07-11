package com.wensaas.core.impl.ops.spi;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.ops.OpsBusinessModuleException;
import com.wensaas.core.api.ops.OpsBusinessModuleService;
import com.wensaas.core.api.pkg.PackageAdminService.PackageStatus;
import com.wensaas.core.api.spi.ModuleMetadata;
import com.wensaas.core.impl.pkg.ModuleApiRouteRegistry;
import com.wensaas.core.impl.pkg.SysPackage;
import com.wensaas.core.impl.pkg.SysPackageMapper;
import com.wensaas.core.impl.pkg.SysPackageModule;
import com.wensaas.core.impl.pkg.SysPackageModuleMapper;
import com.wensaas.core.impl.spi.module.BusinessModuleRegistry;
import com.wensaas.core.impl.spi.registry.DashboardCardRegistry;
import com.wensaas.core.impl.spi.registry.DictRegistry;
import com.wensaas.core.impl.spi.registry.H5PageRegistry;
import com.wensaas.core.impl.spi.registry.NotificationTemplateRegistry;
import com.wensaas.core.impl.spi.registry.PermissionRegistry;
import com.wensaas.core.impl.spi.module.SysBusinessModule;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link OpsBusinessModuleService} 默认实现，支撑运营「业务模块」只读管理页。
 *
 * <p>{@code sys_business_module} 为权威主集（含注册时间，可能含 registry 已无的历史模块）；
 * 贡献能力计数 / 依赖 / API 前缀来自内存 SPI Registry（仅 enabled 模块，缺失则计 0 / 空）。
 */
public class OpsBusinessModuleServiceImpl implements OpsBusinessModuleService {

    private final SysBusinessModuleMapper businessModuleMapper;
    private final BusinessModuleRegistry moduleRegistry;
    private final PermissionRegistry permissionRegistry;
    private final DictRegistry dictRegistry;
    private final DashboardCardRegistry dashboardCardRegistry;
    private final H5PageRegistry h5PageRegistry;
    private final NotificationTemplateRegistry notificationTemplateRegistry;
    private final ModuleApiRouteRegistry apiRouteRegistry;
    private final SysPackageMapper packageMapper;
    private final SysPackageModuleMapper packageModuleMapper;

    public OpsBusinessModuleServiceImpl(SysBusinessModuleMapper businessModuleMapper,
                                        BusinessModuleRegistry moduleRegistry,
                                        PermissionRegistry permissionRegistry,
                                        DictRegistry dictRegistry,
                                        DashboardCardRegistry dashboardCardRegistry,
                                        H5PageRegistry h5PageRegistry,
                                        NotificationTemplateRegistry notificationTemplateRegistry,
                                        ModuleApiRouteRegistry apiRouteRegistry,
                                        SysPackageMapper packageMapper,
                                        SysPackageModuleMapper packageModuleMapper) {
        this.businessModuleMapper = businessModuleMapper;
        this.moduleRegistry = moduleRegistry;
        this.permissionRegistry = permissionRegistry;
        this.dictRegistry = dictRegistry;
        this.dashboardCardRegistry = dashboardCardRegistry;
        this.h5PageRegistry = h5PageRegistry;
        this.notificationTemplateRegistry = notificationTemplateRegistry;
        this.apiRouteRegistry = apiRouteRegistry;
        this.packageMapper = packageMapper;
        this.packageModuleMapper = packageModuleMapper;
    }

    @Override
    public List<ModuleListItem> listModules() {
        return businessModuleMapper.selectList(Wrappers.<SysBusinessModule>lambdaQuery())
                .stream()
                .sorted(Comparator.comparing(SysBusinessModule::getCode))
                .map(m -> {
                    ModuleContribution c = contributionOf(m.getCode());
                    return new ModuleListItem(
                            m.getCode(), m.getName(), m.getVersion(), m.getIcon(),
                            toInstant(m.getCreatedAt()),
                            dependenciesOf(m.getCode()).size(),
                            totalContribution(c));
                })
                .toList();
    }

    @Override
    public ModuleDetail getModule(String code) {
        SysBusinessModule m = businessModuleMapper.selectOne(
                Wrappers.<SysBusinessModule>lambdaQuery().eq(SysBusinessModule::getCode, code));
        if (m == null) {
            throw new OpsBusinessModuleException(OpsBusinessModuleException.Code.MODULE_NOT_FOUND,
                    "业务模块不存在: " + code);
        }
        return new ModuleDetail(
                m.getCode(), m.getName(), m.getDescription(), m.getVersion(), m.getIcon(),
                toInstant(m.getCreatedAt()),
                dependenciesOf(code),
                apiRouteRegistry.prefixesOf(code),
                contributionOf(code),
                relatedPackagesOf(code));
    }

    private ModuleContribution contributionOf(String code) {
        return new ModuleContribution(
                permissionRegistry.countByModule(code),
                dictRegistry.countByModule(code),
                dashboardCardRegistry.countByModule(code),
                h5PageRegistry.countByModule(code),
                notificationTemplateRegistry.countByModule(code));
    }

    private static int totalContribution(ModuleContribution c) {
        return c.permissions() + c.dictGroups()
                + c.dashboardCards() + c.h5Pages() + c.notificationTemplates();
    }

    private List<String> dependenciesOf(String code) {
        return moduleRegistry.find(code).map(ModuleMetadata::dependencies).orElseGet(List::of);
    }

    private List<RelatedPackage> relatedPackagesOf(String code) {
        List<Long> pkgIds = packageModuleMapper.selectList(
                        Wrappers.<SysPackageModule>lambdaQuery().eq(SysPackageModule::getModuleCode, code))
                .stream().map(SysPackageModule::getPackageId).distinct().toList();
        if (pkgIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SysPackage> byId = packageMapper.selectBatchIds(pkgIds).stream()
                .collect(Collectors.toMap(SysPackage::getId, Function.identity()));
        return pkgIds.stream()
                .map(byId::get)
                .filter(p -> p != null)
                .sorted(Comparator.comparing(SysPackage::getCode))
                .map(p -> new RelatedPackage(p.getCode(), p.getName(), packageStatus(p.getStatus())))
                .toList();
    }

    private static String packageStatus(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        try {
            return PackageStatus.fromCode(status).name();
        } catch (IllegalArgumentException e) {
            return "UNKNOWN";
        }
    }

    private static Instant toInstant(LocalDateTime dt) {
        return dt != null ? dt.toInstant(ZoneOffset.of("+08:00")) : null;
    }
}
