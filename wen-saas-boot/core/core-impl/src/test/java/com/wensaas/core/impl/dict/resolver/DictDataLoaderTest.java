package com.wensaas.core.impl.dict.resolver;

import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.dict.SysDictGroup;
import com.wensaas.core.impl.dict.SysDictGroupMapper;
import com.wensaas.core.impl.dict.SysDictItem;
import com.wensaas.core.impl.dict.SysDictItemMapper;

class DictDataLoaderTest {

    private SysDictGroupMapper groupMapper;
    private SysDictItemMapper itemMapper;
    private DictDataLoader loader;

    @BeforeEach
    void setUp() {
        groupMapper = mock(SysDictGroupMapper.class);
        itemMapper = mock(SysDictItemMapper.class);
        loader = new DictDataLoader(groupMapper, itemMapper);
    }

    @Test
    void groupMissing_returnsEmpty() {
        when(groupMapper.selectForTenant(anyString(), anyLong())).thenReturn(null);
        Optional<DictDataLoader.DictGroupSnapshot> r = loader.load(9001L, "MISSING");
        assertThat(r).isEmpty();
        verify(groupMapper).selectForTenant("MISSING", 9001L);
    }

    @Test
    void flatGroup_returnsItemsInOrder() {
        SysDictGroup g = group("GENDER", false);
        when(groupMapper.selectForTenant("GENDER", 9001L)).thenReturn(g);
        when(itemMapper.selectEnabledForTenant("GENDER", 9001L)).thenReturn(List.of(
                item(1L, "MALE", "男", 0L, 1),
                item(2L, "FEMALE", "女", 0L, 2)));

        Optional<DictDataLoader.DictGroupSnapshot> r = loader.load(9001L, "GENDER");
        assertThat(r).isPresent();
        DictDataLoader.DictGroupSnapshot s = r.get();
        assertThat(s.group().supportHierarchy()).isFalse();
        assertThat(s.items()).hasSize(2);
        assertThat(s.items().get(0).code()).isEqualTo("MALE");
        assertThat(s.items().get(0).children()).isEmpty();
        assertThat(s.items().get(1).code()).isEqualTo("FEMALE");
    }

    @Test
    void hierarchicalGroup_buildsTree() {
        SysDictGroup g = group("REGION", true);
        when(groupMapper.selectForTenant("REGION", 9001L)).thenReturn(g);
        when(itemMapper.selectEnabledForTenant("REGION", 9001L)).thenReturn(List.of(
                item(1L, "BJ", "北京", 0L, 1),
                item(2L, "CY", "朝阳",  1L, 1),
                item(3L, "HD", "海淀",  1L, 2),
                item(4L, "SH", "上海", 0L, 2)));

        DictDataLoader.DictGroupSnapshot s = loader.load(9001L, "REGION").orElseThrow();
        assertThat(s.group().supportHierarchy()).isTrue();
        assertThat(s.items()).hasSize(2);
        DictItem bj = s.items().get(0);
        assertThat(bj.code()).isEqualTo("BJ");
        assertThat(bj.children()).extracting(DictItem::code).containsExactly("CY", "HD");
        assertThat(s.items().get(1).code()).isEqualTo("SH");
        assertThat(s.items().get(1).children()).isEmpty();
    }

    @Test
    void groupMapperReceivesTenantId() {
        when(groupMapper.selectForTenant(anyString(), anyLong())).thenReturn(group("X", false));
        when(itemMapper.selectEnabledForTenant(anyString(), anyLong())).thenReturn(List.of());

        loader.load(12345L, "X");
        // 验证 mapper 拿到的 tenantId 是调用方传的（DictDataLoader 不自己读 TenantContext）
        verify(groupMapper).selectForTenant("X", 12345L);
        verify(itemMapper).selectEnabledForTenant("X", 12345L);
    }

    private static SysDictGroup group(String code, boolean hierarchy) {
        SysDictGroup g = new SysDictGroup();
        g.setId(100L);
        g.setTenantId(0L);
        g.setCode(code);
        g.setName("G-" + code);
        g.setSource(DictSource.SYSTEM.code());
        g.setSupportHierarchy(hierarchy);
        g.setStatus(1);
        return g;
    }

    private static SysDictItem item(long id, String code, String name, long parentId, int sortOrder) {
        SysDictItem i = new SysDictItem();
        i.setId(id);
        i.setTenantId(0L);
        i.setGroupCode("G");
        i.setCode(code);
        i.setName(name);
        i.setParentId(parentId);
        i.setSortOrder(sortOrder);
        i.setStatus(1);
        i.setSource(DictSource.SYSTEM.code());
        return i;
    }
}
