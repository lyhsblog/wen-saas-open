package com.wensaas.core.api.spi.def;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DictGroupDefTest {

    @Test
    void ofFactory_keepsSpiV1Compatibility() {
        DictGroupDef def = DictGroupDef.of("crm.customer_source", "客户来源");
        assertThat(def.code()).isEqualTo("crm.customer_source");
        assertThat(def.name()).isEqualTo("客户来源");
        assertThat(def.description()).isNull();
        assertThat(def.supportHierarchy()).isFalse();
        assertThat(def.extraSchema()).isEmpty();
        assertThat(def.presetItems()).isEmpty();
    }

    @Test
    void fullConstructor() {
        DictGroupDef def = new DictGroupDef(
                "region", "地区", "支持省市区", true,
                List.of(new DictExtraFieldDef("code", "行政编码", DictExtraDataType.TEXT, true, null)),
                List.of(DictItemDef.of("BJ", "北京")));
        assertThat(def.supportHierarchy()).isTrue();
        assertThat(def.extraSchema()).hasSize(1);
        assertThat(def.presetItems()).hasSize(1);
    }

    @Test
    void nullCollections_fallbackToEmpty() {
        DictGroupDef def = new DictGroupDef("g", "G", null, false, null, null);
        assertThat(def.extraSchema()).isEmpty();
        assertThat(def.presetItems()).isEmpty();
    }
}
