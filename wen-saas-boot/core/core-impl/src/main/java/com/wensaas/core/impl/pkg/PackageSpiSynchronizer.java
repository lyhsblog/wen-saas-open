package com.wensaas.core.impl.pkg;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.def.PackageDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 把启动期发现的业务模块预置套餐声明同步到 {@code sys_package} / {@code sys_package_module}。
 *
 * <p>策略（insert-if-missing，与字典预置项的"保护运行时修改"同取向）：
 * <ul>
 *   <li>编码不存在 → INSERT 套餐（{@code source_module}=声明模块，默认上架即开箱即用）
 *       + 模块勾选行（声明的 moduleCodes 自动并入声明模块自身）</li>
 *   <li>编码已存在 → 整行跳过，连 name/description 都不更新——套餐是强运营对象，
 *       运营在套餐管理页的修改（改名/调模块集/上下架/归档）是权威，重启不得覆盖</li>
 *   <li>模块下线 → 不操作 DB（租户 plans 可能仍引用，物理清理是运营决策）</li>
 * </ul>
 *
 * <p>{@code source_module} 非空的套餐在 {@code PackageAdminServiceImpl} 侧禁止物理删除
 * （否则重启会被本同步器重新插入而"复活"），运营下线走下架/归档。
 *
 * <p>整个 sync 由 {@link Transactional} 包裹，任一步失败回滚 + 启动失败。
 * 两张表均为纯平台表，无需租户上下文。
 */
public class PackageSpiSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(PackageSpiSynchronizer.class);

    static final int STATUS_DRAFT = PackageAdminServiceImpl.STATUS_DRAFT;
    static final int STATUS_ACTIVE = PackageAdminServiceImpl.STATUS_ACTIVE;

    private final SysPackageMapper packageMapper;
    private final SysPackageModuleMapper packageModuleMapper;

    public PackageSpiSynchronizer(SysPackageMapper packageMapper,
                                   SysPackageModuleMapper packageModuleMapper) {
        this.packageMapper = packageMapper;
        this.packageModuleMapper = packageModuleMapper;
    }

    @Transactional
    public void sync(List<BusinessModuleDescriptor> enabledModules) {
        int created = 0;
        int skipped = 0;
        for (BusinessModuleDescriptor d : enabledModules) {
            String moduleCode = d.metadata().code();
            for (PackageDef def : d.packages()) {
                if (syncOne(moduleCode, def)) {
                    created++;
                } else {
                    skipped++;
                }
            }
        }
        log.info("[Pkg-SPI] 已同步预置套餐：新建 {}、命中 {}", created, skipped);
    }

    /** @return true 表示新建套餐；false 表示已存在（整行跳过） */
    private boolean syncOne(String moduleCode, PackageDef def) {
        SysPackage existing = packageMapper.selectOne(Wrappers.<SysPackage>lambdaQuery()
                .eq(SysPackage::getCode, def.code()));
        if (existing != null) {
            if (!Objects.equals(existing.getSourceModule(), moduleCode)) {
                // 只提示不改写：同码行可能是运营手工建的（source_module 为空）或历史归属漂移，
                // 落库现状是权威
                log.warn("[Pkg-SPI] 套餐 {} 已存在但 source_module={} 与声明模块 {} 不一致，跳过不改写",
                        def.code(), existing.getSourceModule(), moduleCode);
            }
            return false;
        }

        SysPackage pkg = new SysPackage();
        pkg.setCode(def.code());
        pkg.setName(def.name());
        pkg.setDescription(def.description());
        pkg.setIsSystem(false);
        pkg.setStatus(def.publishOnCreate() ? STATUS_ACTIVE : STATUS_DRAFT);
        pkg.setDefaultTtlDays(def.defaultTtlDays());
        pkg.setSourceModule(moduleCode);
        // 启动期无操作人，created_by/updated_by 置空（同其他 SPI 同步器）
        packageMapper.insert(pkg);

        // 声明模块自身隐含并入；TreeSet 兼做去重与稳定排序
        TreeSet<String> moduleCodes = new TreeSet<>(def.moduleCodes());
        moduleCodes.add(moduleCode);
        for (String code : moduleCodes) {
            SysPackageModule row = new SysPackageModule();
            row.setPackageId(pkg.getId());
            row.setModuleCode(code);
            packageModuleMapper.insert(row);
        }
        log.info("[Pkg-SPI] 新增预置套餐 {}（owner={}, status={}, modules={}）",
                def.code(), moduleCode, pkg.getStatus(), moduleCodes);
        return true;
    }
}
