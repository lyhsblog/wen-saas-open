package com.wensaas.core.impl.spi.module;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
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

class BusinessModuleDbSynchronizerTest {

    private SysBusinessModuleMapper mapper;
    private BusinessModuleDbSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        mapper = mock(SysBusinessModuleMapper.class);
        synchronizer = new BusinessModuleDbSynchronizer(mapper);
    }

    @Test
    void newModule_isInserted() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        synchronizer.sync(List.of(module("crm", "1.0.0")));

        ArgumentCaptor<SysBusinessModule> captor = ArgumentCaptor.forClass(SysBusinessModule.class);
        verify(mapper).insert(captor.capture());
        SysBusinessModule row = captor.getValue();
        assertThat(row.getCode()).isEqualTo("crm");
        assertThat(row.getVersion()).isEqualTo("1.0.0");
        verify(mapper, never()).updateById(any(SysBusinessModule.class));
    }

    @Test
    void sameVersion_isSkipped() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingRow("crm", "1.0.0"));

        synchronizer.sync(List.of(module("crm", "1.0.0")));

        verify(mapper, never()).insert(any(SysBusinessModule.class));
        verify(mapper, never()).updateById(any(SysBusinessModule.class));
    }

    @Test
    void sameVersion_metaChanged_syncsDescription() {
        // version 未变但代码侧描述改了（如修正过时措辞）→ 仍应把 DB description 对齐为代码值
        SysBusinessModule existing = existingRow("crm", "1.0.0");
        existing.setDescription("旧描述（过时措辞）");
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        BusinessModuleDescriptor mod = new BusinessModuleDescriptor() {
            @Override
            public ModuleMetadata metadata() {
                return new ModuleMetadata("crm", "crm", "自包含新描述", "1.0.0", null, List.of());
            }
        };
        synchronizer.sync(List.of(mod));

        ArgumentCaptor<SysBusinessModule> captor = ArgumentCaptor.forClass(SysBusinessModule.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("自包含新描述");
        assertThat(captor.getValue().getVersion()).isEqualTo("1.0.0");
        verify(mapper, never()).insert(any(SysBusinessModule.class));
    }

    @Test
    void versionUpgrade_triggersUpdate() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingRow("crm", "1.0.0"));

        synchronizer.sync(List.of(module("crm", "1.2.0")));

        ArgumentCaptor<SysBusinessModule> captor = ArgumentCaptor.forClass(SysBusinessModule.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo("1.2.0");
        verify(mapper, never()).insert(any(SysBusinessModule.class));
    }

    @Test
    void versionDowngrade_stillUpdatesAndWarns() {
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingRow("crm", "1.2.0"));

        synchronizer.sync(List.of(module("crm", "1.0.0")));

        ArgumentCaptor<SysBusinessModule> captor = ArgumentCaptor.forClass(SysBusinessModule.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void moduleNotInDbButOtherIs_leavesOthersUntouched() {
        // 当本次只发现 crm 时，approval 已有的记录不应被触碰
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        synchronizer.sync(List.of(module("crm", "1.0.0")));

        verify(mapper).insert(any(SysBusinessModule.class));
        // 没有 select / update / delete 涉及到 approval —— synchronizer 根本没遍历它
    }

    @Test
    void semVerComparator_handlesMajorMinorPatch() {
        assertThat(BusinessModuleDbSynchronizer.compareSemVer("1.2.0", "1.0.0")).isPositive();
        assertThat(BusinessModuleDbSynchronizer.compareSemVer("1.0.0", "1.2.0")).isNegative();
        assertThat(BusinessModuleDbSynchronizer.compareSemVer("2.0.0", "1.9.9")).isPositive();
        assertThat(BusinessModuleDbSynchronizer.compareSemVer("1.0.0", "1.0.0")).isZero();
        assertThat(BusinessModuleDbSynchronizer.compareSemVer("1.0.0-rc1", "1.0.0-rc2")).isZero();
    }

    private static BusinessModuleDescriptor module(String code, String version) {
        return new BusinessModuleDescriptor() {
            @Override
            public ModuleMetadata metadata() {
                return ModuleMetadata.of(code, code, version);
            }
        };
    }

    private static SysBusinessModule existingRow(String code, String version) {
        SysBusinessModule row = new SysBusinessModule();
        row.setId(1L);
        row.setCode(code);
        row.setName(code);
        row.setVersion(version);
        return row;
    }
}
