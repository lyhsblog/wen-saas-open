package com.wensaas.core.impl.permission.datascope;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataScopeTest {

    @Test
    void fromCode_mapsKnownValues() {
        assertThat(DataScope.fromCode(1)).isEqualTo(DataScope.ALL);
        assertThat(DataScope.fromCode(2)).isEqualTo(DataScope.DEPT_AND_BELOW);
        assertThat(DataScope.fromCode(3)).isEqualTo(DataScope.SELF);
    }

    @Test
    void fromCode_unknownThrows() {
        assertThatThrownBy(() -> DataScope.fromCode(99)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void widest_ALL_dominates() {
        assertThat(DataScope.widest(DataScope.ALL, DataScope.SELF)).isEqualTo(DataScope.ALL);
        assertThat(DataScope.widest(DataScope.SELF, DataScope.ALL)).isEqualTo(DataScope.ALL);
        assertThat(DataScope.widest(DataScope.ALL, DataScope.DEPT_AND_BELOW)).isEqualTo(DataScope.ALL);
    }

    @Test
    void widest_DEPT_beatsSELF() {
        assertThat(DataScope.widest(DataScope.DEPT_AND_BELOW, DataScope.SELF)).isEqualTo(DataScope.DEPT_AND_BELOW);
        assertThat(DataScope.widest(DataScope.SELF, DataScope.DEPT_AND_BELOW)).isEqualTo(DataScope.DEPT_AND_BELOW);
    }

    @Test
    void widest_SELF_only() {
        assertThat(DataScope.widest(DataScope.SELF, DataScope.SELF)).isEqualTo(DataScope.SELF);
    }
}
