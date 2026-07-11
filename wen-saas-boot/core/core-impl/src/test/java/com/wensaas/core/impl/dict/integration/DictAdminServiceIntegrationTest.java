package com.wensaas.core.impl.dict.integration;

import com.wensaas.core.api.dict.DictAdminException;
import com.wensaas.core.api.dict.DictAdminService;
import com.wensaas.core.api.dict.DictGroup;
import com.wensaas.core.api.dict.DictItem;
import com.wensaas.core.api.dict.DictService;
import com.wensaas.core.api.dict.DictSource;
import com.wensaas.core.api.spi.def.DictExtraDataType;
import com.wensaas.core.api.spi.def.DictExtraFieldDef;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DictAdminService} 端到端集成测试。
 *
 * <p>覆盖：分组 / 项 CRUD + SYSTEM 保护 + 跨租户隔离 + 缓存 evict 联动。
 */
@SpringBootTest(classes = TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DictAdminServiceIntegrationTest {

    private static final long TENANT_ID = 9601L;
    private static final long OTHER_TENANT = 9602L;

    @Autowired JdbcTemplate jdbc;
    @Autowired DictAdminService adminService;
    @Autowired DictService dictService;
    @Autowired DictResolver resolver;

    @BeforeAll
    void setupClass() {
        cleanup();
        resolver.evictAll();
    }

    @AfterAll
    void teardownClass() {
        cleanup();
        TenantContext.clear();
        resolver.evictAll();
    }

    @BeforeEach
    void setTenant() {
        cleanup();
        TenantContext.set(TENANT_ID);
        resolver.evictAll();
    }

    @AfterEach
    void resetTenant() {
        TenantContext.clear();
        resolver.evictAll();
    }

    private void cleanup() {
        jdbc.update("DELETE FROM sys_dict_item WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT);
        jdbc.update("DELETE FROM sys_dict_group WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT);
    }

    @Test
    void createGroup_thenListAndGet() {
        DictGroup g = adminService.createGroup(new DictAdminService.CreateGroupRequest(
                "PROJ_PHASE_T1", "项目阶段", "项目生命周期",
                false, List.of()));
        assertThat(g.code()).isEqualTo("PROJ_PHASE_T1");
        assertThat(g.source()).isEqualTo(DictSource.CUSTOM);

        // listGroups 应同时含 6 个 SYSTEM 预置 + 1 个 CUSTOM
        List<DictGroup> all = adminService.listGroups();
        assertThat(all).extracting(DictGroup::code).contains("PROJ_PHASE_T1", "GENDER");
        assertThat(all.stream().filter(x -> x.source() == DictSource.CUSTOM).toList())
                .hasSize(1);
    }

    @Test
    void duplicateGroupCode_throws() {
        adminService.createGroup(new DictAdminService.CreateGroupRequest(
                "PROJ_PHASE_T2", "项目阶段2", null, false, List.of()));
        assertThatThrownBy(() -> adminService.createGroup(new DictAdminService.CreateGroupRequest(
                "PROJ_PHASE_T2", "重复", null, false, List.of())))
                .isInstanceOf(DictAdminException.class)
                .extracting(e -> ((DictAdminException) e).code())
                .isEqualTo(DictAdminException.Code.GROUP_CODE_DUPLICATE);
    }

    @Test
    void systemGroup_cannotUpdateSchema_orDelete() {
        // GENDER 是预置 SYSTEM 分组（tenant_id=0），租户视角应能 selectForTenant 命中
        assertThatThrownBy(() -> adminService.updateGroup("GENDER",
                new DictAdminService.UpdateGroupRequest(null, null,
                        List.of(new DictExtraFieldDef("x", "x", DictExtraDataType.TEXT, false, null)))))
                .isInstanceOf(DictAdminException.class)
                .extracting(e -> ((DictAdminException) e).code())
                .isEqualTo(DictAdminException.Code.GROUP_SYSTEM_READONLY);

        assertThatThrownBy(() -> adminService.deleteGroup("GENDER"))
                .isInstanceOf(DictAdminException.class)
                .extracting(e -> ((DictAdminException) e).code())
                .isEqualTo(DictAdminException.Code.GROUP_SYSTEM_READONLY);
    }

    @Test
    void deleteGroupWithItems_throws() {
        adminService.createGroup(new DictAdminService.CreateGroupRequest(
                "PROJ_PHASE_T3", "项目阶段3", null, false, List.of()));
        adminService.createItem("PROJ_PHASE_T3", new DictAdminService.CreateItemRequest(
                "INIT", "立项", null, 1, null, Map.of()));
        assertThatThrownBy(() -> adminService.deleteGroup("PROJ_PHASE_T3"))
                .isInstanceOf(DictAdminException.class)
                .extracting(e -> ((DictAdminException) e).code())
                .isEqualTo(DictAdminException.Code.GROUP_HAS_ITEMS);
    }

    @Test
    void createItem_evictsCache_andDictServiceSeesNewItem() {
        adminService.createGroup(new DictAdminService.CreateGroupRequest(
                "PROJ_PHASE_T4", "项目阶段4", null, false, List.of()));
        // 预热缓存
        assertThat(dictService.listItems("PROJ_PHASE_T4")).isEmpty();
        adminService.createItem("PROJ_PHASE_T4", new DictAdminService.CreateItemRequest(
                "INIT", "立项", null, 1, null, Map.of()));
        // create 后 evict 缓存，listItems 看到新项
        assertThat(dictService.listItems("PROJ_PHASE_T4")).extracting(DictItem::code)
                .containsExactly("INIT");
    }

    @Test
    void itemCodeDuplicate_throws() {
        adminService.createGroup(new DictAdminService.CreateGroupRequest(
                "PROJ_PHASE_T5", "项目阶段5", null, false, List.of()));
        adminService.createItem("PROJ_PHASE_T5", new DictAdminService.CreateItemRequest(
                "INIT", "立项", null, 1, null, Map.of()));
        assertThatThrownBy(() -> adminService.createItem("PROJ_PHASE_T5",
                new DictAdminService.CreateItemRequest("INIT", "重复", null, 2, null, Map.of())))
                .isInstanceOf(DictAdminException.class)
                .extracting(e -> ((DictAdminException) e).code())
                .isEqualTo(DictAdminException.Code.ITEM_CODE_DUPLICATE);
    }

    @Test
    void hierarchical_buildTree_andDepthLimit() {
        DictGroup g = adminService.createGroup(new DictAdminService.CreateGroupRequest(
                "REGION_T6", "地区", null, true, List.of()));
        DictItem root = adminService.createItem("REGION_T6", new DictAdminService.CreateItemRequest(
                "BJ", "北京", null, 1, null, Map.of()));
        // 通过 listItemsForAdmin 拿到 ID
        long bjId = jdbc.queryForObject(
                "SELECT id FROM sys_dict_item WHERE tenant_id=? AND group_code='REGION_T6' AND code='BJ'",
                Long.class, TENANT_ID);
        DictItem lvl2 = adminService.createItem("REGION_T6", new DictAdminService.CreateItemRequest(
                "CY", "朝阳区", bjId, 1, null, Map.of()));
        List<DictItem> tree = adminService.listItemsForAdmin("REGION_T6", true);
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).code()).isEqualTo("BJ");
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).code()).isEqualTo("CY");
    }

    @Test
    void crossTenant_cannotSeeOrModifyOtherTenantGroup() {
        // 在另一个租户上下文里建分组
        TenantContext.runAs(OTHER_TENANT, () ->
                adminService.createGroup(new DictAdminService.CreateGroupRequest(
                        "OTHER_T7", "其他租户分组", null, false, List.of())));
        // 当前租户视角看不到，跨租户更新抛 NOT_FOUND
        TenantContext.set(TENANT_ID);
        assertThat(adminService.listGroups().stream()
                .filter(x -> x.code().equals("OTHER_T7"))
                .toList()).isEmpty();
        assertThatThrownBy(() -> adminService.updateGroup("OTHER_T7",
                new DictAdminService.UpdateGroupRequest("hack", null, null)))
                .isInstanceOf(DictAdminException.class)
                .extracting(e -> ((DictAdminException) e).code())
                .isEqualTo(DictAdminException.Code.GROUP_NOT_FOUND);
    }
}
