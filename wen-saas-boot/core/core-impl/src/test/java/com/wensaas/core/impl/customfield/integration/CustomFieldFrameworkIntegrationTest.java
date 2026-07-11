package com.wensaas.core.impl.customfield.integration;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.customfield.CustomFieldQuota;
import com.wensaas.core.api.customfield.CustomFieldService;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.namesource.MatchMode;
import com.wensaas.core.api.namesource.NameSearch;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.customfield.def.CreateFieldRequest;
import com.wensaas.core.impl.customfield.def.CustomFieldDefManager;
import com.wensaas.core.impl.customfield.resolver.CustomFieldResolver;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 自定义字段框架端到端集成测试（真 PostgreSQL + 真 Redis）。
 *
 * <p>测试对象 {@code cf_test_obj}：测试自建表（{@code id / tenant_id / extra jsonb}），
 * 集成测试期间动态 CREATE，结束 DROP。对应 {@link CustomFieldObjectDef} 直接注册到
 * {@link CustomFieldObjectRegistry}（不走 BusinessModuleDescriptor SPI 路径，避免触动启动期 validator）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>6 种类型字段全部创建成功（TEXT / LONG_TEXT / NUMBER / DATE / BOOL / ENUM）</li>
 *   <li>索引化 → 检查 {@code pg_indexes} 真有 idx_cf_test_obj_extra_*</li>
 *   <li>dropField 含索引 → pg_indexes 消失 + def deleted_at 非空 + quota 双 dec</li>
 * <li>ENUM 校验真查 dict（预置的 GENDER 分组）</li>
 *   <li>REF 类型校验值为 Long</li>
 *   <li>quota 边界（满 quota 后 createField 抛 FIELD_QUOTA_EXCEEDED）</li>
 *   <li>跨租户隔离（租户 A 字段租户 B 不可见）</li>
 *   <li>toggleIndex / renameField 端到端</li>
 *   <li>validateValues 多类型组合</li>
 * </ul>
 */
@SpringBootTest(classes = {TestApp.class, CustomFieldFrameworkIntegrationTest.RefSearchConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomFieldFrameworkIntegrationTest {

    private static final long TENANT_A = 9601L;
    private static final long TENANT_B = 9602L;
    private static final String OBJECT_CODE = "cf.test_obj";
    private static final String TABLE_NAME = "cf_test_obj";
    private static final String EXTRA_COL = "extra";

    @Autowired JdbcTemplate jdbc;
    @Autowired CustomFieldService customFieldService;
    @Autowired CustomFieldDefManager defManager;
    @Autowired CustomFieldResolver resolver;
    @Autowired CustomFieldObjectRegistry objectRegistry;

    @BeforeAll
    void seed() {
        // 测试自建表（不入 V*.sql，集成测试期间动态管理）
        jdbc.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        jdbc.execute("CREATE TABLE " + TABLE_NAME
                + " (id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 0, extra JSONB)");
        // 注册对象到内存 Registry（绕过 SPI BusinessModuleDescriptor 启动期校验）
        objectRegistry.register("cf", new CustomFieldObjectDef(
                OBJECT_CODE, TABLE_NAME, EXTRA_COL, "集成测试用"));
        cleanup();
    }

    @AfterAll
    void teardown() {
        cleanup();
        jdbc.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        TenantContext.clear();
    }

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT_A);
        cleanup();
        resolver.evictAll(TENANT_A);
        resolver.evictAll(TENANT_B);
    }

    @AfterEach
    void clear() {
        cleanup();
        TenantContext.clear();
        resolver.evictAll(TENANT_A);
        resolver.evictAll(TENANT_B);
    }

    @Test
    void createField_sixBasicTypes_allPersist() {
        defManager.createField(req(TENANT_A, "cf_text",      CustomFieldType.TEXT,      false, false, null, null));
        defManager.createField(req(TENANT_A, "cf_long_text", CustomFieldType.LONG_TEXT, false, false, null, null));
        defManager.createField(req(TENANT_A, "cf_number",    CustomFieldType.NUMBER,    false, false, null, null));
        defManager.createField(req(TENANT_A, "cf_date",      CustomFieldType.DATE,      false, false, null, null));
        defManager.createField(req(TENANT_A, "cf_bool",      CustomFieldType.BOOL,      false, false, null, null));
        defManager.createField(req(TENANT_A, "cf_enum",      CustomFieldType.ENUM,      false, false, "GENDER", null));

        List<CustomFieldDef> defs = customFieldService.listFields(OBJECT_CODE);
        assertThat(defs).hasSize(6);
        assertThat(defs).extracting(CustomFieldDef::fieldCode)
                .containsExactlyInAnyOrder("cf_text", "cf_long_text", "cf_number", "cf_date", "cf_bool", "cf_enum");

        CustomFieldQuota q = customFieldService.getQuota();
        assertThat(q.fieldUsed()).isEqualTo(6);
        assertThat(q.indexUsed()).isZero();
    }

    @Test
    void createField_indexed_createsPgIndex() {
        defManager.createField(req(TENANT_A, "cf_indexed", CustomFieldType.TEXT, false, true, null, null));
        assertThat(pgIndexExists("idx_cf_test_obj_extra_cf_indexed")).isTrue();

        CustomFieldQuota q = customFieldService.getQuota();
        assertThat(q.indexUsed()).isEqualTo(1);
    }

    @Test
    void dropField_indexed_dropsPgIndex_andDecsBothQuotas() {
        Long id = defManager.createField(req(TENANT_A, "cf_drop", CustomFieldType.TEXT, false, true, null, null));
        assertThat(pgIndexExists("idx_cf_test_obj_extra_cf_drop")).isTrue();
        CustomFieldQuota before = customFieldService.getQuota();
        assertThat(before.fieldUsed()).isEqualTo(1);
        assertThat(before.indexUsed()).isEqualTo(1);

        defManager.dropField(TENANT_A, id);
        assertThat(pgIndexExists("idx_cf_test_obj_extra_cf_drop")).isFalse();

        Integer softDeletedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_custom_field_def WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class, id);
        assertThat(softDeletedCount).isEqualTo(1);

        CustomFieldQuota after = customFieldService.getQuota();
        assertThat(after.fieldUsed()).isZero();
        assertThat(after.indexUsed()).isZero();
    }

    @Test
    void toggleIndex_offToOn_thenBack_consistent() {
        Long id = defManager.createField(req(TENANT_A, "cf_toggle", CustomFieldType.TEXT, false, false, null, null));
        assertThat(pgIndexExists("idx_cf_test_obj_extra_cf_toggle")).isFalse();

        defManager.toggleIndex(TENANT_A, id, true);
        assertThat(pgIndexExists("idx_cf_test_obj_extra_cf_toggle")).isTrue();
        assertThat(customFieldService.getQuota().indexUsed()).isEqualTo(1);

        defManager.toggleIndex(TENANT_A, id, false);
        assertThat(pgIndexExists("idx_cf_test_obj_extra_cf_toggle")).isFalse();
        assertThat(customFieldService.getQuota().indexUsed()).isZero();
    }

    @Test
    void renameField_updatesFieldName() {
        Long id = defManager.createField(req(TENANT_A, "cf_rename", CustomFieldType.TEXT, false, false, null, null));
        defManager.renameField(TENANT_A, id, "新名字");
        CustomFieldDef def = customFieldService.getField(OBJECT_CODE, "cf_rename").orElseThrow();
        assertThat(def.fieldName()).isEqualTo("新名字");
    }

    @Test
    void duplicateFieldCode_throws() {
        defManager.createField(req(TENANT_A, "cf_dup", CustomFieldType.TEXT, false, false, null, null));
        assertThatThrownBy(() -> defManager.createField(
                req(TENANT_A, "cf_dup", CustomFieldType.NUMBER, false, false, null, null)))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.DUPLICATE_FIELD_CODE);
    }

    @Test
    void fieldQuotaExceeded_throws() {
        // 把 quota 直接置 1，再尝试创建第 2 个字段
        defManager.createField(req(TENANT_A, "cf_q1", CustomFieldType.TEXT, false, false, null, null));
        jdbc.update("UPDATE sys_custom_field_quota SET field_quota = field_used WHERE tenant_id = ?", TENANT_A);
        resolver.evictQuota(TENANT_A);

        assertThatThrownBy(() -> defManager.createField(
                req(TENANT_A, "cf_q2", CustomFieldType.TEXT, false, false, null, null)))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.FIELD_QUOTA_EXCEEDED);
    }

    @Test
    void crossTenantIsolation_tenantBCannotSeeTenantAFields() {
        defManager.createField(req(TENANT_A, "cf_a_only", CustomFieldType.TEXT, false, false, null, null));

        TenantContext.set(TENANT_B);
        List<CustomFieldDef> defsB = customFieldService.listFields(OBJECT_CODE);
        assertThat(defsB).extracting(CustomFieldDef::fieldCode).doesNotContain("cf_a_only");
    }

    @Test
    void enumField_valueInDict_validates() {
        defManager.createField(req(TENANT_A, "cf_gender", CustomFieldType.ENUM, false, false, "GENDER", null));
        assertThatCode(() -> customFieldService.validateValues(OBJECT_CODE, Map.of("cf_gender", "MALE")))
                .doesNotThrowAnyException();
    }

    @Test
    void enumField_valueNotInDict_throws() {
        defManager.createField(req(TENANT_A, "cf_gender", CustomFieldType.ENUM, false, false, "GENDER", null));
        assertThatThrownBy(() -> customFieldService.validateValues(OBJECT_CODE, Map.of("cf_gender", "ALIEN")))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.ENUM_VALUE_NOT_IN_DICT);
    }

    @Test
    void refField_acceptsLong_rejectsString() {
        defManager.createField(req(TENANT_A, "cf_ref", CustomFieldType.REF, false, false, null, OBJECT_CODE));
        assertThatCode(() -> customFieldService.validateValues(OBJECT_CODE, Map.of("cf_ref", 100L)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> customFieldService.validateValues(OBJECT_CODE, Map.of("cf_ref", "abc")))
                .isInstanceOf(CustomFieldException.class);
    }

    @Test
    void enumField_dictGroupNotFound_atCreateTime_throws() {
        assertThatThrownBy(() -> defManager.createField(
                req(TENANT_A, "cf_bad", CustomFieldType.ENUM, false, false, "NO_SUCH_DICT", null)))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.DICT_GROUP_NOT_FOUND);
    }

    @Test
    void requiredField_validateValuesMissing_throws() {
        defManager.createField(req(TENANT_A, "cf_req", CustomFieldType.TEXT, true, false, null, null));
        assertThatThrownBy(() -> customFieldService.validateValues(OBJECT_CODE, Map.of()))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.INVALID_VALUE);
    }

    @Test
    void cacheHit_thenDirectDbInsert_seesStale_evictThenSeesNew() {
        defManager.createField(req(TENANT_A, "cf_cache", CustomFieldType.TEXT, false, false, null, null));
        List<CustomFieldDef> first = customFieldService.listFields(OBJECT_CODE);
        int firstSize = first.size();

        // 后门直接 jdbc 插入一条软删=null 的 def（绕过 service，不 evict）
        long backdoorId = 950900L;
        jdbc.update("INSERT INTO sys_custom_field_def "
                        + "(id, tenant_id, object_code, field_code, field_name, field_type, "
                        + "is_required, is_indexed, display_order, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
                backdoorId, TENANT_A, OBJECT_CODE, "cf_backdoor", "后门字段", "text", false, false, 0);
        try {
            // 命中缓存：看不到 backdoor
            List<CustomFieldDef> cached = customFieldService.listFields(OBJECT_CODE);
            assertThat(cached).hasSize(firstSize);

            // evict 后能看到
            resolver.evictDefs(TENANT_A, OBJECT_CODE);
            List<CustomFieldDef> reloaded = customFieldService.listFields(OBJECT_CODE);
            assertThat(reloaded).hasSize(firstSize + 1);
        } finally {
            jdbc.update("DELETE FROM sys_custom_field_def WHERE id = ?", backdoorId);
        }
    }

    private static CreateFieldRequest req(long tenant, String code, CustomFieldType type,
                                            boolean required, boolean indexed,
                                            String refDict, String refObject) {
        return new CreateFieldRequest(tenant, OBJECT_CODE, code, "F-" + code, type,
                required, indexed, null, refDict, refObject, 0);
    }

    private boolean pgIndexExists(String indexName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?",
                Integer.class, indexName);
        return count != null && count > 0;
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_custom_field_def WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        jdbc.update("DELETE FROM sys_custom_field_quota WHERE tenant_id IN (?, ?)", TENANT_A, TENANT_B);
        // 防止旧索引残留
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_text");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_long_text");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_number");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_date");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_bool");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_enum");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_indexed");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_drop");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_toggle");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_rename");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_dup");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_q1");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_q2");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_a_only");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_gender");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_ref");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_bad");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_req");
        jdbc.execute("DROP INDEX IF EXISTS idx_cf_test_obj_extra_cf_cache");
    }

    /** REF 目标合法性校验（tenantSearchable && referenceable）走 NameSearch 注册面，测试对象需一并注册。 */
    @TestConfiguration
    static class RefSearchConfig {
        @Bean
        public NameSearch cfTestObjSearch() {
            return new NameSearch() {
                @Override
                public String entityType() {
                    return OBJECT_CODE;
                }

                @Override
                public boolean tenantSearchable() {
                    return true;
                }

                @Override
                public List<FieldMeta> searchFields() {
                    return List.of(new FieldMeta("名称", MatchMode.PREFIX));
                }

                @Override
                public List<NameHit> search(SearchQuery query) {
                    return List.of();
                }
            };
        }
    }
}
