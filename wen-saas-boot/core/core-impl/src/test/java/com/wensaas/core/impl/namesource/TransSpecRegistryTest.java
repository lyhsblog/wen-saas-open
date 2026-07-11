package com.wensaas.core.impl.namesource;

import com.wensaas.core.impl.namesource.NameFillSamples.BadRef;
import com.wensaas.core.impl.namesource.NameFillSamples.CrossView;
import com.wensaas.core.impl.namesource.NameFillSamples.CrossWithoutMarker;
import com.wensaas.core.impl.namesource.NameFillSamples.GuardedWithTransField;
import com.wensaas.core.impl.namesource.NameFillSamples.Item;
import com.wensaas.core.impl.namesource.NameFillSamples.Leaf;
import com.wensaas.core.impl.namesource.NameFillSamples.Page;
import com.wensaas.core.impl.namesource.TransSpecRegistry.ClassSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransSpecRegistryTest {

    private final TransSpecRegistry registry = new TransSpecRegistry();

    @Test
    void parsesTransFields_singleAndMulti() {
        ClassSpec spec = registry.specFor(Item.class);

        assertThat(spec.leaf()).isFalse();
        assertThat(spec.transSpecs()).hasSize(4); // mainDeptName/roleNames/createdByName/ghostName
        assertThat(spec.transSpecs())
                .anySatisfy(s -> {
                    assertThat(s.targetField().getName()).isEqualTo("roleNames");
                    assertThat(s.multi()).isTrue();
                    assertThat(s.cross()).isFalse(); // 默认 FOLLOW
                })
                .anySatisfy(s -> {
                    assertThat(s.targetField().getName()).isEqualTo("mainDeptName");
                    assertThat(s.multi()).isFalse();
                });
        // @TransField 目标 / ref 字段不参与下钻
        assertThat(spec.childFields()).isEmpty();
    }

    @Test
    void crossFieldOnMarkedView_parsesAsCross() {
        ClassSpec spec = registry.specFor(CrossView.class);
        assertThat(spec.transSpecs()).singleElement()
                .satisfies(s -> {
                    assertThat(s.targetField().getName()).isEqualTo("userName");
                    assertThat(s.cross()).isTrue();
                });
    }

    @Test
    void crossFieldWithoutCrossTenantView_failFast() {
        assertThatThrownBy(() -> registry.specFor(CrossWithoutMarker.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@CrossTenantView");
    }

    @Test
    void guardedDtoWithTransField_failFast() {
        assertThatThrownBy(() -> registry.specFor(GuardedWithTransField.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@RelatedObjectGuarded");
    }

    @Test
    void marksDescendableChildFields() {
        ClassSpec spec = registry.specFor(Page.class);

        assertThat(spec.transSpecs()).isEmpty();
        assertThat(spec.leaf()).isFalse(); // 有可下钻字段 records
        assertThat(spec.childFields()).extracting(java.lang.reflect.Field::getName)
                .containsExactly("records"); // title 是 String 叶子，不下钻
    }

    @Test
    void pureLeafClass_markedLeaf() {
        ClassSpec spec = registry.specFor(Leaf.class);
        assertThat(spec.leaf()).isTrue();
        assertThat(spec.transSpecs()).isEmpty();
        assertThat(spec.childFields()).isEmpty();
    }

    @Test
    void transDescendOnScalar_failFast() {
        assertThatThrownBy(() -> registry.specFor(NameFillSamples.BadDescend.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@TransDescend");
    }

    @Test
    void refToNonexistentField_failFastAtFirstParse() {
        assertThatThrownBy(() -> registry.specFor(BadRef.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("doesNotExist");
    }

    @Test
    void cachesPerClass_sameInstanceOnSecondCall() {
        ClassSpec first = registry.specFor(Item.class);
        ClassSpec second = registry.specFor(Item.class);
        assertThat(second).isSameAs(first);
    }
}
