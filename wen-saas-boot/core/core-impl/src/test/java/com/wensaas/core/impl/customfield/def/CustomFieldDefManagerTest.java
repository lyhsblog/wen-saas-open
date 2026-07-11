package com.wensaas.core.impl.customfield.def;

import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.customfield.CustomFieldType;
import com.wensaas.core.api.spi.def.CustomFieldObjectDef;
import com.wensaas.core.impl.namesource.EntitySearchService;
import com.wensaas.core.impl.spi.registry.CustomFieldObjectRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.customfield.quota.CustomFieldQuotaManager;
import com.wensaas.core.impl.customfield.resolver.CustomFieldResolver;

class CustomFieldDefManagerTest {

    private SysCustomFieldDefMapper defMapper;
    private CustomFieldQuotaManager quotaManager;
    private CustomFieldIndexExecutor indexExecutor;
    private CustomFieldObjectRegistry objectRegistry;
    private CustomFieldResolver resolver;
    private CustomFieldValueValidator valueValidator;
    private EntitySearchService entitySearchService;
    private CustomFieldDefManager manager;

    @BeforeEach
    void setUp() {
        defMapper = mock(SysCustomFieldDefMapper.class);
        quotaManager = mock(CustomFieldQuotaManager.class);
        indexExecutor = mock(CustomFieldIndexExecutor.class);
        objectRegistry = mock(CustomFieldObjectRegistry.class);
        resolver = mock(CustomFieldResolver.class);
        valueValidator = mock(CustomFieldValueValidator.class);
        entitySearchService = mock(EntitySearchService.class);
        manager = new CustomFieldDefManager(defMapper, quotaManager, indexExecutor,
                objectRegistry, resolver, valueValidator, entitySearchService);

        when(objectRegistry.findByKey("crm.customer")).thenReturn(java.util.Optional.of(
                new CustomFieldObjectDef("crm.customer", "crm_customer", "extra", null)));
        // 默认：成员可作 ref 目标（其余按各用例显式 stub）
        when(entitySearchService.isTenantReferenceable("sys.user")).thenReturn(true);
    }

    @Test
    void createField_unregisteredObject_throws() {
        when(objectRegistry.findByKey("unknown")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> manager.createField(req("unknown", "cf_src", CustomFieldType.TEXT, false, false, null, null)))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.OBJECT_NOT_REGISTERED);
    }

    @Test
    void createField_illegalFieldCode_throws() {
        assertThatThrownBy(() -> manager.createField(req("crm.customer", "Source!", CustomFieldType.TEXT, false, false, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(defMapper, never()).insert(any(SysCustomFieldDef.class));
    }

    @Test
    void createField_missingCfPrefix_throws() {
        // 合法 identifier 但不带 cf_ 前缀（v2 约束）
        assertThatThrownBy(() -> manager.createField(req("crm.customer", "source", CustomFieldType.TEXT, false, false, null, null)))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.INVALID_FIELD_CODE_PREFIX);
        verify(defMapper, never()).insert(any(SysCustomFieldDef.class));
    }

    @Test
    void createField_duplicateCode_throws() {
        SysCustomFieldDef exists = new SysCustomFieldDef();
        when(defMapper.findByTenantObjectAndCode(9001L, "crm.customer", "cf_src")).thenReturn(exists);
        assertThatThrownBy(() -> manager.createField(req("crm.customer", "cf_src", CustomFieldType.TEXT, false, false, null, null)))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.DUPLICATE_FIELD_CODE);
    }

    @Test
    void createField_enumMissingRefDict_throws() {
        assertThatThrownBy(() -> manager.createField(req("crm.customer", "cf_src", CustomFieldType.ENUM, false, false, null, null)))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.MISSING_REF_CODE);
    }

    @Test
    void createField_refMissingRefObject_throws() {
        assertThatThrownBy(() -> manager.createField(req("crm.customer", "cf_src", CustomFieldType.REF, false, false, null, null)))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.MISSING_REF_CODE);
    }

    @Test
    void createField_refInvalidTarget_throws() {
        // no.such 未注册 → isTenantReferenceable=false（mock 默认）→ 非法 ref 目标
        assertThatThrownBy(() -> manager.createField(
                req("crm.customer", "cf_link", CustomFieldType.REF, false, false, null, "no.such")))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.MISSING_REF_CODE);
        verify(defMapper, never()).insert(any(SysCustomFieldDef.class));
    }

    @Test
    void createField_refRoleTarget_throws() {
        // 角色 referenceable=false（权限构件、非数据记录）→ isTenantReferenceable=false → 不可作 ref 目标
        when(entitySearchService.isTenantReferenceable("sys.role")).thenReturn(false);
        assertThatThrownBy(() -> manager.createField(
                req("crm.customer", "cf_role", CustomFieldType.REF, false, false, null, "sys.role")))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.MISSING_REF_CODE);
        verify(defMapper, never()).insert(any(SysCustomFieldDef.class));
    }

    @Test
    void createField_refReferenceableTarget_ok() {
        // 成员 isTenantReferenceable=true → 合法 ref 目标
        when(defMapper.findByTenantObjectAndCode(anyLong(), anyString(), anyString())).thenReturn(null);
        manager.createField(
                req("crm.customer", "cf_user", CustomFieldType.REF, false, false, null, "sys.user"));
        verify(defMapper).insert(any(SysCustomFieldDef.class));
    }

    @Test
    void createField_notIndexed_skipsIndexQuota_andDdl() {
        when(defMapper.findByTenantObjectAndCode(anyLong(), anyString(), anyString())).thenReturn(null);
        manager.createField(req("crm.customer", "cf_src", CustomFieldType.TEXT, false, false, null, null));
        verify(quotaManager).incFieldUsageOrThrow(9001L);
        verify(quotaManager, never()).incIndexUsageOrThrow(anyLong());
        verify(indexExecutor, never()).createIndex(anyString(), anyString(), anyString());
        verify(defMapper).insert(any(SysCustomFieldDef.class));
        verify(resolver).evictDefs(9001L, "crm.customer");
        verify(resolver).evictQuota(9001L);
    }

    @Test
    void createField_indexed_createsIndex_andOccupiesIndexQuota() {
        when(defMapper.findByTenantObjectAndCode(anyLong(), anyString(), anyString())).thenReturn(null);
        manager.createField(req("crm.customer", "cf_src", CustomFieldType.TEXT, false, true, null, null));
        verify(quotaManager).incFieldUsageOrThrow(9001L);
        verify(quotaManager).incIndexUsageOrThrow(9001L);
        verify(indexExecutor).createIndex("crm_customer", "extra", "cf_src");
    }

    @Test
    void createField_quotaExceeded_skipsDdl() {
        when(defMapper.findByTenantObjectAndCode(anyLong(), anyString(), anyString())).thenReturn(null);
        doThrow(new CustomFieldException(CustomFieldException.Code.FIELD_QUOTA_EXCEEDED, "x"))
                .when(quotaManager).incFieldUsageOrThrow(anyLong());
        assertThatThrownBy(() -> manager.createField(req("crm.customer", "cf_src", CustomFieldType.TEXT, false, true, null, null)))
                .isInstanceOf(CustomFieldException.class);
        verify(defMapper, never()).insert(any(SysCustomFieldDef.class));
        verify(indexExecutor, never()).createIndex(anyString(), anyString(), anyString());
    }

    @Test
    void renameField_updatesName() {
        when(defMapper.selectById(100L)).thenReturn(rowAt(100L, 9001L, "crm.customer", "cf_src", false));
        manager.renameField(9001L, 100L, "新来源");
        verify(defMapper).updateById(any(SysCustomFieldDef.class));
        verify(resolver).evictDefs(9001L, "crm.customer");
    }

    @Test
    void renameField_notFound_throws() {
        when(defMapper.selectById(100L)).thenReturn(null);
        assertThatThrownBy(() -> manager.renameField(9001L, 100L, "x"))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.FIELD_NOT_FOUND);
    }

    @Test
    void renameField_wrongTenant_throws() {
        when(defMapper.selectById(100L)).thenReturn(rowAt(100L, 8888L, "crm.customer", "cf_src", false));
        assertThatThrownBy(() -> manager.renameField(9001L, 100L, "x"))
                .isInstanceOf(CustomFieldException.class);
    }

    @Test
    void dropField_indexed_dropsIndex_andDecsBoth() {
        when(defMapper.selectById(100L)).thenReturn(rowAt(100L, 9001L, "crm.customer", "cf_src", true));
        manager.dropField(9001L, 100L);
        verify(indexExecutor).dropIndex("crm_customer", "cf_src");
        verify(quotaManager).decIndexUsage(9001L);
        verify(quotaManager).decFieldUsage(9001L);
        verify(defMapper).deleteById(100L);
    }

    @Test
    void dropField_notIndexed_skipsDropIndex() {
        when(defMapper.selectById(100L)).thenReturn(rowAt(100L, 9001L, "crm.customer", "cf_src", false));
        manager.dropField(9001L, 100L);
        verify(indexExecutor, never()).dropIndex(anyString(), anyString());
        verify(quotaManager, never()).decIndexUsage(anyLong());
        verify(quotaManager).decFieldUsage(9001L);
    }

    @Test
    void toggleIndex_noOp_whenSame() {
        when(defMapper.selectById(100L)).thenReturn(rowAt(100L, 9001L, "crm.customer", "cf_src", false));
        manager.toggleIndex(9001L, 100L, false);
        verify(indexExecutor, never()).createIndex(anyString(), anyString(), anyString());
        verify(indexExecutor, never()).dropIndex(anyString(), anyString());
    }

    @Test
    void toggleIndex_offToOn_createsAndIncQuota() {
        when(defMapper.selectById(100L)).thenReturn(rowAt(100L, 9001L, "crm.customer", "cf_src", false));
        manager.toggleIndex(9001L, 100L, true);
        verify(quotaManager).incIndexUsageOrThrow(9001L);
        verify(indexExecutor).createIndex("crm_customer", "extra", "cf_src");
        verify(defMapper).updateById(any(SysCustomFieldDef.class));
    }

    @Test
    void toggleIndex_onToOff_dropsAndDecQuota() {
        when(defMapper.selectById(100L)).thenReturn(rowAt(100L, 9001L, "crm.customer", "cf_src", true));
        manager.toggleIndex(9001L, 100L, false);
        verify(indexExecutor).dropIndex("crm_customer", "cf_src");
        verify(quotaManager).decIndexUsage(9001L);
    }

    @Test
    void dropField_indexed_withOtherTenantPeer_skipsPhysicalDrop_butDecsQuota() {
        // 架构审查 M13：共享索引仍有其他租户在用 → 不物理 DROP，但本租户额度照常释放。
        when(defMapper.selectById(100L)).thenReturn(rowAt(100L, 9001L, "crm.customer", "cf_src", true));
        when(defMapper.countOtherIndexedPeers("crm.customer", "cf_src", 100L)).thenReturn(1L);
        manager.dropField(9001L, 100L);
        verify(indexExecutor, never()).dropIndex(anyString(), anyString());
        verify(quotaManager).decIndexUsage(9001L);
        verify(quotaManager).decFieldUsage(9001L);
        verify(defMapper).deleteById(100L);
    }

    @Test
    void toggleIndex_onToOff_withOtherTenantPeer_skipsPhysicalDrop() {
        when(defMapper.selectById(100L)).thenReturn(rowAt(100L, 9001L, "crm.customer", "cf_src", true));
        when(defMapper.countOtherIndexedPeers("crm.customer", "cf_src", 100L)).thenReturn(2L);
        manager.toggleIndex(9001L, 100L, false);
        verify(indexExecutor, never()).dropIndex(anyString(), anyString());
        verify(quotaManager).decIndexUsage(9001L);
        verify(defMapper).updateById(any(SysCustomFieldDef.class));
    }

    @Test
    void createField_enum_ensuresDictExists() {
        when(defMapper.findByTenantObjectAndCode(anyLong(), anyString(), anyString())).thenReturn(null);
        manager.createField(req("crm.customer", "cf_gender", CustomFieldType.ENUM, false, false, "GENDER", null));
        verify(valueValidator).ensureDictGroupExists("GENDER");
    }

    private static CreateFieldRequest req(String objectCode, String fieldCode,
                                            CustomFieldType type, boolean required,
                                            boolean indexed, String refDict, String refObject) {
        return new CreateFieldRequest(9001L, objectCode, fieldCode, "Name", type,
                required, indexed, null, refDict, refObject, 0);
    }

    private static SysCustomFieldDef rowAt(long id, long tenantId, String object, String fieldCode, boolean indexed) {
        SysCustomFieldDef r = new SysCustomFieldDef();
        r.setId(id);
        r.setTenantId(tenantId);
        r.setObjectCode(object);
        r.setFieldCode(fieldCode);
        r.setFieldName("F-" + fieldCode);
        r.setFieldType(CustomFieldType.TEXT.code());
        r.setIsRequired(false);
        r.setIsIndexed(indexed);
        r.setDisplayOrder(0);
        return r;
    }
}
