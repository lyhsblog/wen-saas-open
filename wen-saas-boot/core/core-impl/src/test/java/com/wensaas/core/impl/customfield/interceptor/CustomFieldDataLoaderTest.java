package com.wensaas.core.impl.customfield.interceptor;

import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.customfield.CustomFieldQuota;
import com.wensaas.core.api.customfield.CustomFieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.customfield.def.SysCustomFieldDef;
import com.wensaas.core.impl.customfield.def.SysCustomFieldDefMapper;
import com.wensaas.core.impl.customfield.quota.SysCustomFieldQuota;
import com.wensaas.core.impl.customfield.quota.SysCustomFieldQuotaMapper;

class CustomFieldDataLoaderTest {

    private SysCustomFieldDefMapper defMapper;
    private SysCustomFieldQuotaMapper quotaMapper;
    private CustomFieldDataLoader loader;

    @BeforeEach
    void setUp() {
        defMapper = mock(SysCustomFieldDefMapper.class);
        quotaMapper = mock(SysCustomFieldQuotaMapper.class);
        loader = new CustomFieldDataLoader(defMapper, quotaMapper);
    }

    @Test
    void loadDefs_emptyMapper_returnsEmptyList() {
        when(defMapper.listByTenantAndObject(anyLong(), anyString())).thenReturn(List.of());
        assertThat(loader.loadDefs(9001L, "crm.customer")).isEmpty();
    }

    @Test
    void loadDefs_converts_toApiRecord() {
        SysCustomFieldDef row = new SysCustomFieldDef();
        row.setId(100L);
        row.setTenantId(9001L);
        row.setObjectCode("crm.customer");
        row.setFieldCode("source");
        row.setFieldName("来源");
        row.setFieldType("text");
        row.setIsRequired(true);
        row.setIsIndexed(false);
        row.setDisplayOrder(5);
        when(defMapper.listByTenantAndObject(9001L, "crm.customer")).thenReturn(List.of(row));

        List<CustomFieldDef> defs = loader.loadDefs(9001L, "crm.customer");
        assertThat(defs).hasSize(1);
        CustomFieldDef d = defs.get(0);
        assertThat(d.id()).isEqualTo(100L);
        assertThat(d.fieldCode()).isEqualTo("source");
        assertThat(d.type()).isEqualTo(CustomFieldType.TEXT);
        assertThat(d.required()).isTrue();
        assertThat(d.indexed()).isFalse();
        assertThat(d.displayOrder()).isEqualTo(5);
    }

    @Test
    void loadQuota_missing_returnsEmpty() {
        when(quotaMapper.selectById(9001L)).thenReturn(null);
        assertThat(loader.loadQuota(9001L)).isEmpty();
    }

    @Test
    void loadQuota_existing_mapsFields() {
        SysCustomFieldQuota row = new SysCustomFieldQuota();
        row.setTenantId(9001L);
        row.setFieldQuota(25);
        row.setFieldUsed(4);
        row.setIndexQuota(6);
        row.setIndexUsed(2);
        when(quotaMapper.selectById(9001L)).thenReturn(row);

        Optional<CustomFieldQuota> q = loader.loadQuota(9001L);
        assertThat(q).isPresent();
        assertThat(q.get().fieldQuota()).isEqualTo(25);
        assertThat(q.get().fieldRemaining()).isEqualTo(21);
        assertThat(q.get().indexUsed()).isEqualTo(2);
    }
}
