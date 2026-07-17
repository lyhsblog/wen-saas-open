package com.wensaas.core.impl.pkg;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
import com.wensaas.core.api.spi.def.PackageDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageSpiSynchronizerTest {

    private SysPackageMapper packageMapper;
    private SysPackageModuleMapper packageModuleMapper;
    private PackageSpiSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        packageMapper = mock(SysPackageMapper.class);
        packageModuleMapper = mock(SysPackageModuleMapper.class);
        synchronizer = new PackageSpiSynchronizer(packageMapper, packageModuleMapper);
        // 模拟 MyBatis-Plus ASSIGN_ID 回填主键
        when(packageMapper.insert(any(SysPackage.class))).thenAnswer(inv -> {
            inv.getArgument(0, SysPackage.class).setId(100L);
            return 1;
        });
    }

    @Test
    void newPackage_insertedActiveWithOwnerModuleImplied() {
        when(packageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        synchronizer.sync(List.of(module("crm_demo",
                PackageDef.of("crm_demo.starter", "入门包", "描述"))));

        ArgumentCaptor<SysPackage> pkgCaptor = ArgumentCaptor.forClass(SysPackage.class);
        verify(packageMapper).insert(pkgCaptor.capture());
        SysPackage pkg = pkgCaptor.getValue();
        assertThat(pkg.getCode()).isEqualTo("crm_demo.starter");
        assertThat(pkg.getName()).isEqualTo("入门包");
        assertThat(pkg.getStatus()).isEqualTo(PackageSpiSynchronizer.STATUS_ACTIVE);
        assertThat(pkg.getIsSystem()).isFalse();
        assertThat(pkg.getSourceModule()).isEqualTo("crm_demo");
        assertThat(pkg.getDefaultTtlDays()).isNull();

        // 声明模块自身隐含并入
        ArgumentCaptor<SysPackageModule> moduleCaptor = ArgumentCaptor.forClass(SysPackageModule.class);
        verify(packageModuleMapper).insert(moduleCaptor.capture());
        assertThat(moduleCaptor.getValue().getPackageId()).isEqualTo(100L);
        assertThat(moduleCaptor.getValue().getModuleCode()).isEqualTo("crm_demo");
    }

    @Test
    void crossModuleRefs_dedupedAndSorted_withSelfIncluded() {
        when(packageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        synchronizer.sync(List.of(module("crm",
                new PackageDef("crm.suite", "套件", null,
                        List.of("oa", "crm", "hr"), 365, true))));

        ArgumentCaptor<SysPackageModule> captor = ArgumentCaptor.forClass(SysPackageModule.class);
        verify(packageModuleMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(SysPackageModule::getModuleCode)
                .containsExactly("crm", "hr", "oa");
    }

    @Test
    void publishOnCreateFalse_insertsDraft() {
        when(packageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        synchronizer.sync(List.of(module("crm",
                new PackageDef("crm.pro", "进阶包", null, List.of(), null, false))));

        ArgumentCaptor<SysPackage> captor = ArgumentCaptor.forClass(SysPackage.class);
        verify(packageMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PackageSpiSynchronizer.STATUS_DRAFT);
    }

    @Test
    void existingPackage_isEntirelySkipped() {
        // 运营可能已改名/调过模块集，重启不得覆盖：连 name 都不更新
        SysPackage existing = new SysPackage();
        existing.setId(1L);
        existing.setCode("crm_demo.starter");
        existing.setName("运营改过的名字");
        existing.setSourceModule("crm_demo");
        when(packageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        synchronizer.sync(List.of(module("crm_demo",
                PackageDef.of("crm_demo.starter", "入门包", null))));

        verify(packageMapper, never()).insert(any(SysPackage.class));
        verify(packageMapper, never()).updateById(any(SysPackage.class));
        verify(packageModuleMapper, never()).insert(any(SysPackageModule.class));
    }

    @Test
    void sourceModuleDrift_onlyWarns_neverWrites() {
        // 同码行是运营手工建的（source_module 为空）→ 跳过且不改写归属
        SysPackage existing = new SysPackage();
        existing.setId(1L);
        existing.setCode("crm_demo.starter");
        existing.setName("运营手工包");
        existing.setSourceModule(null);
        when(packageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        synchronizer.sync(List.of(module("crm_demo",
                PackageDef.of("crm_demo.starter", "入门包", null))));

        verify(packageMapper, never()).insert(any(SysPackage.class));
        verify(packageMapper, never()).updateById(any(SysPackage.class));
    }

    private BusinessModuleDescriptor module(String code, PackageDef... packages) {
        return new BusinessModuleDescriptor() {
            @Override public ModuleMetadata metadata() { return ModuleMetadata.of(code, code, "1.0.0"); }
            @Override public List<PackageDef> packages() { return List.of(packages); }
        };
    }
}
