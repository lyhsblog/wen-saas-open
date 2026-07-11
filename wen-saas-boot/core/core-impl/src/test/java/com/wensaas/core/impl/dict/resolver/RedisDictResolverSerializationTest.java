package com.wensaas.core.impl.dict.resolver;

import com.wensaas.core.api.dict.DictGroup;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisDictResolverSerializationTest {

    @Test
    void roundTrip_flatGroup() {
        DictGroup g = new DictGroup("GENDER", "性别", null, DictSource.SYSTEM, false, List.of());
        List<DictItem> items = List.of(
                new DictItem(1001L, "MALE",   "男",   null, 1, 1, null, DictSource.SYSTEM, Map.of(), List.of()),
                new DictItem(1002L, "FEMALE", "女",   null, 2, 1, null, DictSource.SYSTEM, Map.of(), List.of()));
        DictDataLoader.DictGroupSnapshot src = new DictDataLoader.DictGroupSnapshot(g, items);

        String json = RedisDictResolver.serialize(src);
        DictDataLoader.DictGroupSnapshot back = RedisDictResolver.deserialize(json);

        assertThat(back.group().code()).isEqualTo("GENDER");
        assertThat(back.group().source()).isEqualTo(DictSource.SYSTEM);
        assertThat(back.items()).hasSize(2);
        assertThat(back.items().get(0).code()).isEqualTo("MALE");
        assertThat(back.items().get(1).name()).isEqualTo("女");
    }

    @Test
    void roundTrip_withChildren() {
        DictGroup g = new DictGroup("REGION", "地区", null, DictSource.SYSTEM, true, List.of());
        DictItem cy = new DictItem(2001L, "CY", "朝阳", "BJ", 1, 1, null, DictSource.SYSTEM, Map.of(), List.of());
        DictItem bj = new DictItem(2000L, "BJ", "北京", null, 1, 1, null, DictSource.SYSTEM, Map.of(), List.of(cy));
        DictDataLoader.DictGroupSnapshot src = new DictDataLoader.DictGroupSnapshot(g, List.of(bj));

        String json = RedisDictResolver.serialize(src);
        DictDataLoader.DictGroupSnapshot back = RedisDictResolver.deserialize(json);

        assertThat(back.items()).hasSize(1);
        assertThat(back.items().get(0).children()).hasSize(1);
        assertThat(back.items().get(0).children().get(0).code()).isEqualTo("CY");
    }

    @Test
    void keyOf_format() {
        // region 内 key（完整 Redis key 由 CacheRegion 叠加 wen-saas-boot:cache:dict: 前缀）
        assertThat(RedisDictResolver.keyOf(9001L, "GENDER")).isEqualTo("9001:GENDER");
    }
}
