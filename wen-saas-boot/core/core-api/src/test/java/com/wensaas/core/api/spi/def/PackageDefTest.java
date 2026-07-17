package com.wensaas.core.api.spi.def;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackageDefTest {

    @Test
    void ofFactory_defaults() {
        PackageDef def = PackageDef.of("crm_demo.starter", "入门包", "描述");
        assertThat(def.code()).isEqualTo("crm_demo.starter");
        assertThat(def.name()).isEqualTo("入门包");
        assertThat(def.description()).isEqualTo("描述");
        assertThat(def.moduleCodes()).isEmpty();
        assertThat(def.defaultTtlDays()).isNull();
        assertThat(def.publishOnCreate()).isTrue();
    }

    @Test
    void fullConstructor() {
        PackageDef def = new PackageDef("crm.suite", "套件包", null,
                List.of("oa", "hr"), 365, false);
        assertThat(def.moduleCodes()).containsExactly("oa", "hr");
        assertThat(def.defaultTtlDays()).isEqualTo(365);
        assertThat(def.publishOnCreate()).isFalse();
    }

    @Test
    void nullModuleCodes_fallbackToEmpty() {
        PackageDef def = new PackageDef("crm.p", "P", null, null, null, true);
        assertThat(def.moduleCodes()).isEmpty();
    }

    @Test
    void blankCodeOrName_throws() {
        assertThatThrownBy(() -> new PackageDef(null, "P", null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
        assertThatThrownBy(() -> new PackageDef("crm.p", " ", null, null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void nonPositiveTtl_throws() {
        assertThatThrownBy(() -> new PackageDef("crm.p", "P", null, null, 0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultTtlDays");
        assertThatThrownBy(() -> new PackageDef("crm.p", "P", null, null, -30, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
