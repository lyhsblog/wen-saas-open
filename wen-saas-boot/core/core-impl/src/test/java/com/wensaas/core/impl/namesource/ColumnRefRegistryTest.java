package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.impl.namesource.ColumnRefRegistry.Ref;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnRefRegistryTest {

    /** 真实扫描 classpath：底座已标注的 3 个 @DisplayRef 字段应被收集（含 label）。 */
    @Test
    void scan_picksUpAnnotatedBaseEntities() {
        ColumnRefRegistry registry = new ColumnRefRegistry();

        assertThat(registry.refOf("sys_tenant", "csm_user_id"))
                .isEqualTo(new Ref(EntityTypes.SYS_USER, "CSM"));
        assertThat(registry.refOf("sys_role_permission", "role_id"))
                .isEqualTo(new Ref(EntityTypes.SYS_ROLE, "角色"));
        assertThat(registry.refOf("sys_package_module", "package_id"))
                .isEqualTo(new Ref(EntityTypes.SYS_PACKAGE, "套餐"));
    }

    @Test
    void unknownOrNull_returnsNull() {
        ColumnRefRegistry registry = new ColumnRefRegistry();

        assertThat(registry.refOf("sys_tenant", "name")).isNull();
        assertThat(registry.refOf("no_such_table", "x")).isNull();
        assertThat(registry.refOf(null, "x")).isNull();
        assertThat(registry.refOf("sys_tenant", null)).isNull();
    }

    @Test
    void prebuiltMap_constructor() {
        ColumnRefRegistry registry = new ColumnRefRegistry(
                Map.of("t", Map.of("c", new Ref("e.type", "标签"))));

        assertThat(registry.refOf("t", "c")).isEqualTo(new Ref("e.type", "标签"));
        assertThat(registry.refOf("t", "other")).isNull();
    }
}
