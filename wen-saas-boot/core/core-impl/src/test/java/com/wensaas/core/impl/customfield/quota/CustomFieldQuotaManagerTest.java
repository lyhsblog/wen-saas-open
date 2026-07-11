package com.wensaas.core.impl.customfield.quota;

import com.wensaas.core.api.customfield.CustomFieldException;
import com.wensaas.core.api.customfield.CustomFieldQuota;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.wensaas.core.impl.customfield.CustomFieldProperties;

class CustomFieldQuotaManagerTest {

    private SysCustomFieldQuotaMapper mapper;
    private CustomFieldProperties props;
    private CustomFieldQuotaManager manager;

    @BeforeEach
    void setUp() {
        mapper = mock(SysCustomFieldQuotaMapper.class);
        props = new CustomFieldProperties();
        manager = new CustomFieldQuotaManager(mapper, props);
    }

    @Test
    void getOrInit_existing_returnsRow() {
        when(mapper.selectById(9001L)).thenReturn(row(9001L, 20, 3, 5, 1));
        CustomFieldQuota q = manager.getOrInit(9001L);
        assertThat(q.fieldUsed()).isEqualTo(3);
        assertThat(q.indexUsed()).isEqualTo(1);
    }

    @Test
    void getOrInit_missing_inserts_default() {
        when(mapper.selectById(9001L)).thenReturn(null);
        when(mapper.insert(any(SysCustomFieldQuota.class))).thenReturn(1);

        CustomFieldQuota q = manager.getOrInit(9001L);
        assertThat(q.fieldQuota()).isEqualTo(20);
        assertThat(q.indexQuota()).isEqualTo(5);
        assertThat(q.fieldUsed()).isZero();
        verify(mapper).insert(any(SysCustomFieldQuota.class));
    }

    @Test
    void incFieldUsage_succeeds_whenAffected() {
        when(mapper.selectById(anyLong())).thenReturn(row(9001L, 20, 3, 5, 1));
        when(mapper.tryIncFieldUsed(9001L)).thenReturn(1);

        manager.incFieldUsageOrThrow(9001L);
        verify(mapper).tryIncFieldUsed(9001L);
    }

    @Test
    void incFieldUsage_quotaExceeded_throws() {
        when(mapper.selectById(anyLong())).thenReturn(row(9001L, 20, 20, 5, 1));
        when(mapper.tryIncFieldUsed(9001L)).thenReturn(0);

        assertThatThrownBy(() -> manager.incFieldUsageOrThrow(9001L))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.FIELD_QUOTA_EXCEEDED);
    }

    @Test
    void incIndexUsage_quotaExceeded_throws() {
        when(mapper.selectById(anyLong())).thenReturn(row(9001L, 20, 3, 5, 5));
        when(mapper.tryIncIndexUsed(9001L)).thenReturn(0);

        assertThatThrownBy(() -> manager.incIndexUsageOrThrow(9001L))
                .isInstanceOf(CustomFieldException.class)
                .matches(e -> ((CustomFieldException) e).code()
                        == CustomFieldException.Code.INDEX_QUOTA_EXCEEDED);
    }

    @Test
    void dec_callsMapper() {
        manager.decFieldUsage(9001L);
        manager.decIndexUsage(9001L);
        verify(mapper).decFieldUsed(9001L);
        verify(mapper).decIndexUsed(9001L);
    }

    private static SysCustomFieldQuota row(long tenantId, int fq, int fu, int iq, int iu) {
        SysCustomFieldQuota r = new SysCustomFieldQuota();
        r.setTenantId(tenantId);
        r.setFieldQuota(fq);
        r.setFieldUsed(fu);
        r.setIndexQuota(iq);
        r.setIndexUsed(iu);
        return r;
    }
}
