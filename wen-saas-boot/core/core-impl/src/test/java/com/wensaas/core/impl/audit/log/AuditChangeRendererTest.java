package com.wensaas.core.impl.audit.log;

import tools.jackson.databind.ObjectMapper;
import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.ops.audit.OpsOperationLogQueryService.DataChange;
import com.wensaas.core.api.ops.audit.OpsOperationLogQueryService.FieldChange;
import com.wensaas.core.impl.namesource.ColumnRefRegistry;
import com.wensaas.core.impl.namesource.ColumnRefRegistry.Ref;
import com.wensaas.core.impl.namesource.EntityNameResolver;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditChangeRendererTest {

    /** 计数桩：记录 batchLookup 被调次数，验证整页批量（不逐格 N+1）。 */
    static final class CountingLookup implements NameLookup {
        private final String type;
        private final Map<Long, String> table;
        int calls = 0;

        CountingLookup(String type, Map<Long, String> table) {
            this.type = type;
            this.table = table;
        }

        @Override
        public String entityType() {
            return type;
        }

        @Override
        public String displayLabel() {
            return type;
        }

        @Override
        public Map<Long, String> batchLookup(Collection<Long> ids) {
            calls++;
            Map<Long, String> out = new java.util.HashMap<>();
            for (Long id : ids) {
                String n = table.get(id);
                if (n != null) {
                    out.put(id, n);
                }
            }
            return out;
        }
    }

    private static SysAuditTableConfig cfg(String table, String col, String label, String render) {
        SysAuditTableConfig c = new SysAuditTableConfig();
        c.setTableName(table);
        c.setColumnName(col);
        c.setLabel(label);
        c.setRender(render);
        return c;
    }

    private static SysDataChangeLog row(String table, String op, String pk, String changes) {
        SysDataChangeLog r = new SysDataChangeLog();
        r.setTableName(table);
        r.setOp(op);
        r.setRowPk(pk);
        r.setChanges(changes);
        return r;
    }

    private AuditChangeRenderer build(EntityNameResolver resolver, SysAuditTableConfig... configs) {
        return build(resolver, new ColumnRefRegistry(Map.of()), configs);
    }

    private AuditChangeRenderer build(EntityNameResolver resolver, ColumnRefRegistry refRegistry,
                                      SysAuditTableConfig... configs) {
        SysAuditTableConfigMapper mapper = mock(SysAuditTableConfigMapper.class);
        when(mapper.selectList(isNull())).thenReturn(List.of(configs));
        return new AuditChangeRenderer(mapper, resolver, refRegistry, new ObjectMapper());
    }

    @Test
    void ref_resolvesToNameWithId_andEnumTranslated() {
        EntityNameResolver resolver = new EntityNameResolver(List.of(
                new CountingLookup("sys.role", Map.of(10L, "管理员"))));
        AuditChangeRenderer renderer = build(resolver,
                cfg("sys_role", "name", "名称", "SCALAR"),
                cfg("sys_role_permission", "role_id", "角色", "REF:sys.role"),
                cfg("sys_role", "status", "状态", "ENUM:commonStatus"));

        List<DataChange> out = renderer.render(List.of(
                row("sys_role_permission", "INSERT", "1",
                        "{\"role_id\":{\"before\":null,\"after\":10}}"),
                row("sys_role", "UPDATE", "10",
                        "{\"status\":{\"before\":1,\"after\":0}}")));

        FieldChange roleFc = out.get(0).fields().get(0);
        assertThat(roleFc.label()).isEqualTo("角色");
        assertThat(roleFc.before()).isNull();
        assertThat(roleFc.after()).isEqualTo("管理员(10)");

        FieldChange statusFc = out.get(1).fields().get(0);
        assertThat(statusFc.before()).isEqualTo("启用");
        assertThat(statusFc.after()).isEqualTo("停用");
    }

    @Test
    void ref_idNotResolvable_fallsBackToRawId() {
        EntityNameResolver resolver = new EntityNameResolver(List.of(
                new CountingLookup("sys.role", Map.of(10L, "管理员"))));
        AuditChangeRenderer renderer = build(resolver,
                cfg("sys_role_permission", "role_id", "角色", "REF:sys.role"));

        List<DataChange> out = renderer.render(List.of(
                row("sys_role_permission", "INSERT", "1",
                        "{\"role_id\":{\"before\":null,\"after\":88}}")));

        assertThat(out.get(0).fields().get(0).after()).isEqualTo("88"); // 漂移/删除回退原 id
    }

    @Test
    void ref_batchedAcrossRows_singleLookupCall() {
        CountingLookup lookup = new CountingLookup("sys.role", Map.of(10L, "管理员", 20L, "财务"));
        EntityNameResolver resolver = new EntityNameResolver(List.of(lookup));
        AuditChangeRenderer renderer = build(resolver,
                cfg("sys_role_permission", "role_id", "角色", "REF:sys.role"));

        List<DataChange> out = renderer.render(List.of(
                row("sys_role_permission", "INSERT", "1", "{\"role_id\":{\"before\":null,\"after\":10}}"),
                row("sys_role_permission", "INSERT", "2", "{\"role_id\":{\"before\":null,\"after\":20}}"),
                row("sys_role_permission", "INSERT", "3", "{\"role_id\":{\"before\":10,\"after\":20}}")));

        assertThat(out.get(0).fields().get(0).after()).isEqualTo("管理员(10)");
        assertThat(out.get(1).fields().get(0).after()).isEqualTo("财务(20)");
        assertThat(out.get(2).fields().get(0).before()).isEqualTo("管理员(10)");
        // 整页一次批量：10/20 去重后仅 1 次 batchLookup
        assertThat(lookup.calls).isEqualTo(1);
    }

    @Test
    void scalarAndMask_passThrough_unconfiguredFallsBackToColumn() {
        EntityNameResolver resolver = new EntityNameResolver(List.of());
        AuditChangeRenderer renderer = build(resolver,
                cfg("sys_role", "name", "名称", "SCALAR"),
                cfg("sys_role", "secret", "密钥", "MASK"));

        List<DataChange> out = renderer.render(List.of(
                row("sys_role", "UPDATE", "10",
                        "{\"name\":{\"before\":\"旧\",\"after\":\"新\"},"
                                + "\"secret\":{\"before\":\"<已变更>\",\"after\":\"<已变更>\"},"
                                + "\"misc\":{\"before\":\"a\",\"after\":\"b\"}}")));

        Map<String, FieldChange> byCol = new java.util.HashMap<>();
        for (FieldChange f : out.get(0).fields()) {
            byCol.put(f.column(), f);
        }
        assertThat(byCol.get("name").after()).isEqualTo("新");
        assertThat(byCol.get("secret").after()).isEqualTo("<已变更>");
        // 未配置列：label 回退列名、值原样
        assertThat(byCol.get("misc").label()).isEqualTo("misc");
        assertThat(byCol.get("misc").after()).isEqualTo("b");
    }

    @Test
    void annotationDrivenRef_noConfigRow_usesRegistryEntityTypeAndLabel() {
        EntityNameResolver resolver = new EntityNameResolver(List.of(
                new CountingLookup("sys.user", Map.of(99L, "张三"))));
        // 无任何审计配置行；REF 完全来自 @DisplayRef（ColumnRefRegistry）
        ColumnRefRegistry refRegistry = new ColumnRefRegistry(
                Map.of("sys_tenant", Map.of("csm_user_id", new Ref("sys.user", "CSM"))));
        AuditChangeRenderer renderer = build(resolver, refRegistry /* 无 configs */);

        List<DataChange> out = renderer.render(List.of(
                row("sys_tenant", "UPDATE", "5",
                        "{\"csm_user_id\":{\"before\":null,\"after\":99}}")));

        FieldChange fc = out.get(0).fields().get(0);
        assertThat(fc.label()).isEqualTo("CSM");      // 标签来自注解
        assertThat(fc.after()).isEqualTo("张三(99)");  // 名称来自 EntityNameResolver + 审计补 (id)
    }

    @Test
    void explicitConfigRender_winsOverRegistry() {
        EntityNameResolver resolver = new EntityNameResolver(List.of());
        ColumnRefRegistry refRegistry = new ColumnRefRegistry(
                Map.of("sys_tenant", Map.of("status", new Ref("sys.user", "误标"))));
        // 同列既有注解又有显式 config：config 的 ENUM 优先
        AuditChangeRenderer renderer = build(resolver, refRegistry,
                cfg("sys_tenant", "status", "状态", "ENUM:tenantStatus"));

        List<DataChange> out = renderer.render(List.of(
                row("sys_tenant", "UPDATE", "5", "{\"status\":{\"before\":1,\"after\":2}}")));

        FieldChange fc = out.get(0).fields().get(0);
        assertThat(fc.label()).isEqualTo("状态");
        assertThat(fc.after()).isEqualTo("已过期");
    }
}
