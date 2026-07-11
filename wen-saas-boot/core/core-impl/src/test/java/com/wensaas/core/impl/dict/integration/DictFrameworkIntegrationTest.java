package com.wensaas.core.impl.dict.integration;

import com.wensaas.core.api.auth.user.UserContext;
import com.wensaas.core.api.dict.DictGroup;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.dict.DictSource;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.dict.resolver.DictResolver;
import com.wensaas.core.impl.tenant.integration.TestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字典框架端到端集成测试（真 PostgreSQL + 真 Redis）。
 *
 * <p>验证：
 * <ul>
 * <li> 6 个 SYSTEM 预置分组存在</li>
 *   <li>GENDER / EDUCATION / COMPANY_SIZE 预置项数</li>
 *   <li>DictService 查 SYSTEM 分组</li>
 *   <li>CUSTOM 分组与 SYSTEM 合并视图（CUSTOM 优先）</li>
 *   <li>Redis 缓存命中 + DB 变更需 evict</li>
 *   <li>batchGetItemNames 返回过滤后 map</li>
 * </ul>
 *
 * <p>前置：dev 已运行过一次，wen_saas 库已应用初始化迁移。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DictFrameworkIntegrationTest {

    private static final long TENANT_ID = 9501L;
    private static final long CUSTOM_GROUP_ID = 950001L;
    private static final long CUSTOM_ITEM_ID_1 = 950101L;
    private static final long CUSTOM_ITEM_ID_2 = 950102L;
    private static final long CUSTOM_OVERRIDE_ID = 950103L;

    @Autowired JdbcTemplate jdbc;
    @Autowired DictService dictService;
    @Autowired DictResolver dictResolver;

    @BeforeAll
    void seed() {
        cleanup();
        // 租户自定义分组：PROJECT_STAGE
        jdbc.update("INSERT INTO sys_dict_group (id, tenant_id, code, name, source, support_hierarchy) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                CUSTOM_GROUP_ID, TENANT_ID, "PROJECT_STAGE", "项目阶段", DictSource.CUSTOM.code(), false);
        jdbc.update("INSERT INTO sys_dict_item (id, tenant_id, group_code, code, name, sort_order, source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                CUSTOM_ITEM_ID_1, TENANT_ID, "PROJECT_STAGE", "INITIATING", "立项", 1, DictSource.CUSTOM.code());
        jdbc.update("INSERT INTO sys_dict_item (id, tenant_id, group_code, code, name, sort_order, source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                CUSTOM_ITEM_ID_2, TENANT_ID, "PROJECT_STAGE", "EXECUTING", "执行中", 2, DictSource.CUSTOM.code());
        // 租户对 GENDER 同名 code "MALE" 的覆盖项（验证 CUSTOM 优先）—— 实际产品 v2 才做，
        // 这里仅放一条 tenant 自有项到 GENDER 分组，证明 tenant_id IN (0, X) 合并
        jdbc.update("INSERT INTO sys_dict_item (id, tenant_id, group_code, code, name, sort_order, source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                CUSTOM_OVERRIDE_ID, TENANT_ID, "GENDER", "X_OTHER", "其他（租户扩展）", 99, DictSource.CUSTOM.code());
        TenantContext.set(TENANT_ID);
        // 清缓存防止前一次测试残留
        dictResolver.evictAll();
    }

    @AfterAll
    void teardown() {
        cleanup();
        TenantContext.clear();
        dictResolver.evictAll();
    }

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void clearContexts() {
        UserContext.clear();
        TenantContext.clear();
        dictResolver.evictAll();
    }

    @Test
    void systemGroups_allSixPresent() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_group WHERE tenant_id = 0 AND source = 1 AND deleted_at IS NULL",
                Integer.class);
        assertThat(count).isEqualTo(6);
    }

    @Test
    void genderGroup_threeItems() {
        DictGroup g = dictService.getGroup("GENDER").orElseThrow();
        assertThat(g.code()).isEqualTo("GENDER");
        assertThat(g.source()).isEqualTo(DictSource.SYSTEM);

        // listItems 应合并 tenant_id=0 的 3 项 + 当前租户追加的 X_OTHER = 4 项
        List<DictItem> items = dictService.listItems("GENDER");
        assertThat(items).extracting(DictItem::code)
                .containsExactlyInAnyOrder("MALE", "FEMALE", "UNKNOWN", "X_OTHER");
    }

    @Test
    void educationGroup_sevenItems() {
        List<DictItem> items = dictService.listItems("EDUCATION");
        assertThat(items).hasSize(7);
        assertThat(items).extracting(DictItem::code).contains("BACHELOR", "DOCTOR");
    }

    @Test
    void companySizeGroup_sixItems_orderedBySortOrder() {
        List<DictItem> items = dictService.listItems("COMPANY_SIZE");
        assertThat(items).hasSize(6);
        assertThat(items.get(0).code()).isEqualTo("1-49");
        assertThat(items.get(5).code()).isEqualTo("10000+");
    }

    @Test
    void customGroup_visibleToOwnerTenant() {
        DictGroup g = dictService.getGroup("PROJECT_STAGE").orElseThrow();
        assertThat(g.source()).isEqualTo(DictSource.CUSTOM);
        assertThat(g.supportHierarchy()).isFalse();

        List<DictItem> items = dictService.listItems("PROJECT_STAGE");
        assertThat(items).extracting(DictItem::code).containsExactly("INITIATING", "EXECUTING");
    }

    @Test
    void batchGetItemNames_returnsOnlyExisting() {
        Map<String, String> names = dictService.batchGetItemNames(
                "GENDER", List.of("MALE", "FEMALE", "NOT_EXIST"));
        assertThat(names).hasSize(2)
                .containsEntry("MALE", "男")
                .containsEntry("FEMALE", "女")
                .doesNotContainKey("NOT_EXIST");
    }

    @Test
    void cacheHit_thenEvict_seesDbChange() {
        // 1) 第一次 listItems → DB 装载 + Redis 写缓存
        List<DictItem> first = dictService.listItems("COMPANY_SIZE");
        assertThat(first).hasSize(6);

        // 2) DB 后门加一项（不调 service，直接 jdbc）
        long extraId = 950201L;
        jdbc.update("INSERT INTO sys_dict_item (id, tenant_id, group_code, code, name, sort_order, source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                extraId, TENANT_ID, "COMPANY_SIZE", "TEST_99", "测试", 99, DictSource.CUSTOM.code());
        try {
            // 3) 不 evict 时缓存命中，仍 6 项
            List<DictItem> cached = dictService.listItems("COMPANY_SIZE");
            assertThat(cached).hasSize(6);

            // 4) evict 后重新装载，看到 7 项
            dictResolver.evict(TENANT_ID, "COMPANY_SIZE");
            List<DictItem> reloaded = dictService.listItems("COMPANY_SIZE");
            assertThat(reloaded).hasSize(7);
        } finally {
            jdbc.update("DELETE FROM sys_dict_item WHERE id = ?", extraId);
            dictResolver.evict(TENANT_ID, "COMPANY_SIZE");
        }
    }

    @Test
    void missingGroup_returnsEmpty_andCachesNullMarker() {
        Optional<DictGroup> r = dictService.getGroup("NOT_A_GROUP");
        assertThat(r).isEmpty();
        // 第二次也是 empty（命中缓存的 NULL marker，不会再次 DB 查询；本断言仅是行为一致性）
        assertThat(dictService.getGroup("NOT_A_GROUP")).isEmpty();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_dict_item WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM sys_dict_group WHERE tenant_id = ?", TENANT_ID);
    }
}
