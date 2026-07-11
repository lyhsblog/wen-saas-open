package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.RenderMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RenderModeTest {

    @Test
    void primary_returnsPrimaryOnly_ignoringSecondary() {
        assertThat(RenderMode.PRIMARY.render("张三", "U001")).isEqualTo("张三");
    }

    @Test
    void both_appendsSecondary_orFallsBackToPrimary() {
        assertThat(RenderMode.BOTH.render("管理员", "ADMIN")).isEqualTo("管理员(ADMIN)");
        assertThat(RenderMode.BOTH.render("管理员", null)).isEqualTo("管理员");
        assertThat(RenderMode.BOTH.render("管理员", "  ")).isEqualTo("管理员");
    }

    @Test
    void blankPrimary_returnsNull_forAllModes() {
        for (RenderMode m : RenderMode.values()) {
            assertThat(m.render(null, "S")).isNull();
            assertThat(m.render("   ", "S")).isNull();
        }
    }
}
