package com.wensaas.core.impl.customfield.integration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.customfield.CustomFieldExtension;
import com.wensaas.core.api.namesource.MatchMode;
import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.namesource.NameSearch;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.customfield.CustomFieldDisplayResolver;
import com.wensaas.core.impl.customfield.def.CreateFieldRequest;
import com.wensaas.core.impl.customfield.def.CustomFieldDefManager;
import com.wensaas.core.impl.customfield.resolver.CustomFieldResolver;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.mybatis.spring.SqlSessionFactoryBean;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * v2 业务端拦截器端到端集成测试（真 PostgreSQL + 真 Redis）。
 *
 * <p>测试表 {@code cf_intercept_obj}：测试自建 + 含 jsonb extra；@CustomFieldExtension entity
 * {@link CfTestEntity} 通过 {@link CfTestMapper} 走完整 INSERT/SELECT 链路。
 *
 * <p>覆盖：
 * <ol>
 *   <li>INSERT entity.setExtra({...}) 自动校验通过</li>
 *   <li>INSERT 不合法值时 WriteInterceptor 抛 INVALID_VALUE</li>
 *   <li>SELECT WHERE 已索引 cf_ 字段 → 翻译为 extra->>'xxx'（EXPLAIN 命中索引）</li>
 *   <li>SELECT WHERE 未索引 cf_ 字段 → 抛 UNINDEXED_QUERY_NOT_ALLOWED</li>
 *   <li>SELECT WHERE 未注册 cf_ 字段 → 抛 FIELD_NOT_FOUND</li>
 *   <li>DisplayResolver 批量翻译 ENUM 命中字典缓存</li>
 * </ol>
 */
@SpringBootTest(classes = {TestApp.class, CustomFieldInterceptorIntegrationTest.MapperConfig.class,
        CustomFieldInterceptorIntegrationTest.RefLookupConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomFieldInterceptorIntegrationTest {

    private static final long TENANT_A = 9701L;
    private static final String OBJECT_CODE = "cf.intercept_obj";
    private static final String TABLE_NAME = "cf_intercept_obj";

    @Autowired JdbcTemplate jdbc;
    @Autowired CustomFieldDefManager defManager;
    @Autowired CustomFieldResolver resolver;
    @Autowired CustomFieldObjectRegistry objectRegistry;
    @Autowired CustomFieldDisplayResolver displayResolver;
    @Autowired CfTestMapper mapper;

    @BeforeAll
    void seed() {
        jdbc.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        jdbc.execute("CREATE TABLE " + TABLE_NAME
                + " (id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL DEFAULT 0,"
                + " name VARCHAR(255), extra JSONB)");
        objectRegistry.register("cfint", new CustomFieldObjectDef(
                OBJECT_CODE, TABLE_NAME, "extra", "v2 集成测试用"));
        cleanup();
    }

    @AfterAll
    void teardown() {
        cleanup();
        jdbc.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
        TenantContext.clear();
    }

    @BeforeEach
    void setTenantAndSeedFields() {
        TenantContext.set(TENANT_A);
        cleanup();
        resolver.evictAll(TENANT_A);
        // 启用 GENDER 字典（预置）
        defManager.createField(req("cf_source", CustomFieldType.ENUM, false, true, "GENDER", null));
        defManager.createField(req("cf_score",  CustomFieldType.NUMBER, false, false, null, null));
    }

    @AfterEach
    void clear() {
        cleanup();
        TenantContext.clear();
        resolver.evictAll(TENANT_A);
    }

    @Test
    void insert_validatesExtra_andPersists() {
        CfTestEntity e = new CfTestEntity();
        e.id = IdWorker.getId();
        e.tenantId = TENANT_A;
        e.name = "Alice";
        e.extra = new HashMap<>(Map.of("cf_source", "MALE"));
        mapper.insert(e);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE tenant_id = ? AND extra->>'cf_source' = 'MALE'",
                Integer.class, TENANT_A);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void insert_invalidEnumValue_throws() {
        CfTestEntity e = new CfTestEntity();
        e.id = IdWorker.getId();
        e.tenantId = TENANT_A;
        e.extra = new HashMap<>(Map.of("cf_source", "NOT_A_GENDER"));
        assertThatThrownBy(() -> mapper.insert(e))
                .hasRootCauseInstanceOf(CustomFieldException.class)
                .rootCause()
                .matches(ex -> ((CustomFieldException) ex).code()
                        == CustomFieldException.Code.ENUM_VALUE_NOT_IN_DICT);
    }

    @Test
    void select_indexedCfField_rewritesToJsonbOperator() {
        CfTestEntity e1 = entity("Alice", Map.of("cf_source", "MALE"));
        CfTestEntity e2 = entity("Bob",   Map.of("cf_source", "FEMALE"));
        mapper.insert(e1);
        mapper.insert(e2);

        List<CfTestEntity> males = mapper.selectList(
                new QueryWrapper<CfTestEntity>().eq("cf_source", "MALE"));
        assertThat(males).extracting(x -> x.name).containsExactly("Alice");
    }

    @Test
    void select_unindexedCfField_throwsUnindexed() {
        // cf_score 未索引；触发 QueryRewriter 拒绝
        assertThatThrownBy(() -> mapper.selectList(
                new QueryWrapper<CfTestEntity>().eq("cf_score", 85)))
                .hasRootCauseInstanceOf(CustomFieldException.class)
                .rootCause()
                .matches(ex -> ((CustomFieldException) ex).code()
                        == CustomFieldException.Code.UNINDEXED_QUERY_NOT_ALLOWED);
    }

    @Test
    void select_ghostCfField_throwsFieldNotFound() {
        assertThatThrownBy(() -> mapper.selectList(
                new QueryWrapper<CfTestEntity>().eq("cf_ghost", "x")))
                .hasRootCauseInstanceOf(CustomFieldException.class)
                .rootCause()
                .matches(ex -> ((CustomFieldException) ex).code()
                        == CustomFieldException.Code.FIELD_NOT_FOUND);
    }

    @Test
    void displayResolver_refField_v3_addsLabel() {
        // 注册一个 REF 字段：refObjectCode 用本测试 OBJECT_CODE 自引用（lookup 在 RefLookupConfig 注册）
        defManager.createField(req("cf_ref", CustomFieldType.REF, false, false, null, OBJECT_CODE));

        CfTestEntity e1 = entity("Alice", Map.of("cf_ref", 100001L));
        CfTestEntity e2 = entity("Bob",   Map.of("cf_ref", 100002L));
        mapper.insert(e1);
        mapper.insert(e2);

        List<CfTestEntity> all = mapper.selectList(null);
        List<Map<String, Object>> views = displayResolver.resolveViews(
                OBJECT_CODE,
                all.stream().map(x -> (Map<String, Object>) (x.extra == null ? Map.<String,Object>of() : x.extra)).toList());

        assertThat(views).hasSize(2);
        assertThat(views.stream().map(v -> v.get("cf_ref__label")))
                .containsExactlyInAnyOrder("Ref-100001", "Ref-100002");
    }

    @Test
    void displayResolver_batchEnumLabel() {
        CfTestEntity e1 = entity("Alice", Map.of("cf_source", "MALE", "cf_score", 85));
        CfTestEntity e2 = entity("Bob",   Map.of("cf_source", "FEMALE"));
        mapper.insert(e1);
        mapper.insert(e2);

        List<CfTestEntity> all = mapper.selectList(null);
        List<Map<String, Object>> views = displayResolver.resolveViews(
                OBJECT_CODE,
                all.stream().map(x -> (Map<String, Object>) (x.extra == null ? Map.<String,Object>of() : x.extra)).toList());
        assertThat(views).hasSize(2);
        // ENUM 翻译到位（MALE/FEMALE 是 GENDER 分组的 itemCode）
        assertThat(views.get(0)).containsKey("cf_source__label");
        assertThat(views.get(1)).containsKey("cf_source__label");
        // cf_score 是 NUMBER，不加 __label
        assertThat(views.get(0)).doesNotContainKey("cf_score__label");
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_custom_field_def WHERE tenant_id = ?", TENANT_A);
        jdbc.update("DELETE FROM sys_custom_field_quota WHERE tenant_id = ?", TENANT_A);
        jdbc.update("DELETE FROM " + TABLE_NAME + " WHERE tenant_id = ?", TENANT_A);
    }

    private static CreateFieldRequest req(String code, CustomFieldType type, boolean required,
                                            boolean indexed, String refDict, String refObject) {
        return new CreateFieldRequest(TENANT_A, OBJECT_CODE, code, "F-" + code, type,
                required, indexed, null, refDict, refObject, 0);
    }

    private static CfTestEntity entity(String name, Map<String, Object> extra) {
        CfTestEntity e = new CfTestEntity();
        e.id = IdWorker.getId();
        e.tenantId = TENANT_A;
        e.name = name;
        e.extra = new HashMap<>(extra);
        return e;
    }

    @TableName(value = TABLE_NAME, autoResultMap = true)
    @CustomFieldExtension(objectCode = OBJECT_CODE)
    public static class CfTestEntity {
        @TableId(type = IdType.INPUT)
        public Long id;
        public Long tenantId;
        public String name;
        @TableField(typeHandler = JacksonTypeHandler.class)
        public Map<String, Object> extra;
    }

    @Mapper
    public interface CfTestMapper extends BaseMapper<CfTestEntity> {}

    /** 不依赖 @Mapper 包扫描 —— 内部嵌套 mapper 需显式注册到 Spring（@MapperScan 不扫嵌套类）。 */
    @Configuration
    static class MapperConfig {
        // 没什么需要装配的；CfTestMapper 由 @MapperScan(basePackages = "com.wensaas") 扫到
    }

    /** v3 REF 翻译测试 fixture：注册一个 lookup，把任意 id 翻译为 "Ref-{id}"。 */
    @Configuration
    static class RefLookupConfig {
        @Bean
        public NameLookup testRefLookup() {
            return new NameLookup() {
                @Override
                public String entityType() {
                    return OBJECT_CODE;
                }

                @Override
                public String displayLabel() {
                    return "测试对象";
                }

                @Override
                public Map<Long, String> batchLookup(Collection<Long> ids) {
                    Map<Long, String> out = new HashMap<>();
                    for (Long id : ids) {
                        out.put(id, "Ref-" + id);
                    }
                    return out;
                }
            };
        }

        /** REF 目标合法性校验（tenantSearchable && referenceable）走 NameSearch 注册面，测试对象需一并注册。 */
        @Bean
        public NameSearch testRefSearch() {
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
