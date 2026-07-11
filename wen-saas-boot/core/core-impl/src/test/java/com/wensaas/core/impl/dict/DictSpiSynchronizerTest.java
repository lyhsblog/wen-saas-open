package com.wensaas.core.impl.dict;

import com.wensaas.core.api.dict.DictSource;
import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
import com.wensaas.core.api.spi.def.DictExtraDataType;
import com.wensaas.core.api.spi.def.DictExtraFieldDef;
import com.wensaas.core.api.spi.def.DictGroupDef;
import com.wensaas.core.api.spi.def.DictItemDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DictSpiSynchronizerTest {

    private SysDictGroupMapper groupMapper;
    private SysDictItemMapper itemMapper;
    private DictExtraSchemaValidator validator;
    private DictSpiSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        groupMapper = mock(SysDictGroupMapper.class);
        itemMapper = mock(SysDictItemMapper.class);
        validator = new DictExtraSchemaValidator();
        synchronizer = new DictSpiSynchronizer(groupMapper, itemMapper, validator);
    }

    @Test
    void newGroup_insertGroupAndPresetItems() {
        when(groupMapper.selectByCodeAndSource(anyLong(), anyString(), anyInt())).thenReturn(null);

        BusinessModuleDescriptor d = descriptor("crm",
                new DictGroupDef("crm.customer_source", "客户来源", null, false, List.of(),
                        List.of(DictItemDef.of("WEB", "官网"),
                                DictItemDef.of("OFFLINE", "线下"))));
        synchronizer.sync(List.of(d));

        ArgumentCaptor<SysDictGroup> groupCap = ArgumentCaptor.forClass(SysDictGroup.class);
        verify(groupMapper).insert(groupCap.capture());
        SysDictGroup g = groupCap.getValue();
        assertThat(g.getCode()).isEqualTo("crm.customer_source");
        assertThat(g.getSource()).isEqualTo(DictSource.MODULE.code());
        assertThat(g.getModuleCode()).isEqualTo("crm");
        assertThat(g.getTenantId()).isZero();

        ArgumentCaptor<SysDictItem> itemCap = ArgumentCaptor.forClass(SysDictItem.class);
        verify(itemMapper, times(2)).insert(itemCap.capture());
        List<SysDictItem> items = itemCap.getAllValues();
        assertThat(items).extracting(SysDictItem::getCode).containsExactly("WEB", "OFFLINE");
        assertThat(items).extracting(SysDictItem::getSource).containsOnly(DictSource.MODULE.code());
        assertThat(items).extracting(SysDictItem::getParentId).containsOnly(0L);
    }

    @Test
    void existingGroup_updateSchemaOnChange() {
        SysDictGroup existing = new SysDictGroup();
        existing.setId(999L);
        existing.setTenantId(0L);
        existing.setCode("crm.x");
        existing.setName("旧名");
        existing.setSource(DictSource.MODULE.code());
        existing.setModuleCode("crm");
        existing.setSupportHierarchy(false);
        existing.setExtraSchema(List.of());
        when(groupMapper.selectByCodeAndSource(0L, "crm.x", DictSource.MODULE.code())).thenReturn(existing);

        List<DictExtraFieldDef> newSchema = List.of(
                new DictExtraFieldDef("k", "K", DictExtraDataType.TEXT, false, null));
        BusinessModuleDescriptor d = descriptor("crm",
                new DictGroupDef("crm.x", "新名", "desc", false, newSchema, List.of()));
        synchronizer.sync(List.of(d));

        verify(groupMapper).updateById(existing);
        // 不应再 insert items（已存在分组的 preset 重启不动）
        verify(itemMapper, never()).insert(any(SysDictItem.class));
        assertThat(existing.getName()).isEqualTo("新名");
        assertThat(existing.getExtraSchema()).hasSize(1);
    }

    @Test
    void existingGroup_noChange_noUpdate() {
        SysDictGroup existing = new SysDictGroup();
        existing.setId(1L);
        existing.setCode("g");
        existing.setName("G");
        existing.setSupportHierarchy(false);
        existing.setSource(DictSource.MODULE.code());
        existing.setModuleCode("m");
        existing.setExtraSchema(List.of());
        when(groupMapper.selectByCodeAndSource(0L, "g", DictSource.MODULE.code())).thenReturn(existing);

        BusinessModuleDescriptor d = descriptor("m",
                new DictGroupDef("g", "G", null, false, List.of(), List.of()));
        synchronizer.sync(List.of(d));

        verify(groupMapper, never()).updateById(any(SysDictGroup.class));
        verify(groupMapper, never()).insert(any(SysDictGroup.class));
    }

    @Test
    void invalidPresetItem_throwsAndAbortsBeforeInsert() {
        when(groupMapper.selectByCodeAndSource(anyLong(), anyString(), anyInt())).thenReturn(null);

        List<DictExtraFieldDef> schema = List.of(
                new DictExtraFieldDef("level", "层级", DictExtraDataType.NUMBER, true, null));
        BusinessModuleDescriptor d = descriptor("m",
                new DictGroupDef("g", "G", null, false, schema,
                        List.of(new DictItemDef("X", "X", null, null, null,
                                Map.of("level", "not a number")))));

        assertThatThrownBy(() -> synchronizer.sync(List.of(d)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NUMBER");
        verify(groupMapper, never()).insert(any(SysDictGroup.class));
        verify(itemMapper, never()).insert(any(SysDictItem.class));
    }

    @Test
    void emptyModuleList_noOp() {
        synchronizer.sync(List.of());
        verify(groupMapper, never()).insert(any(SysDictGroup.class));
        verify(groupMapper, never()).updateById(any(SysDictGroup.class));
        verify(itemMapper, never()).insert(any(SysDictItem.class));
    }

    private static BusinessModuleDescriptor descriptor(String code, DictGroupDef... dicts) {
        ModuleMetadata meta = ModuleMetadata.of(code, "Mod-" + code, "1.0.0");
        return new BusinessModuleDescriptor() {
            @Override
            public ModuleMetadata metadata() { return meta; }
            @Override
            public List<DictGroupDef> dictGroups() { return List.of(dicts); }
        };
    }
}
