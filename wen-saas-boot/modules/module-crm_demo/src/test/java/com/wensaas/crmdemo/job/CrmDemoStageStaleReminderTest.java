package com.wensaas.crmdemo.job;

import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.RecipientSpec;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.api.tenant.TenantQueryService;
import com.wensaas.crmdemo.entity.CrmDemoCustomer;
import com.wensaas.crmdemo.repository.CrmDemoCustomerMapper;
import com.wensaas.crmdemo.spi.CrmDemoModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CrmDemoStageStaleReminder} 扫描逻辑单测（超期筛选本身在 SQL 条件里，
 * 这里验证租户遍历 / 按负责人聚合 / 模板变量与收件人）。
 */
@ExtendWith(MockitoExtension.class)
class CrmDemoStageStaleReminderTest {

    @Mock
    private CrmDemoCustomerMapper mapper;
    @Mock
    private MessageService messageService;
    @Mock
    private TenantQueryService tenantQuery;

    private CrmDemoStageStaleReminder newReminder() {
        return new CrmDemoStageStaleReminder(mapper, messageService, tenantQuery, new CrmDemoProperties());
    }

    private static CrmDemoCustomer customer(long id, Long ownerId) {
        CrmDemoCustomer c = new CrmDemoCustomer();
        c.setId(id);
        c.setName("客户" + id);
        c.setStatus("LEAD");
        c.setOwnerId(ownerId);
        return c;
    }

    @Test
    void 按负责人聚合_每人一条汇总提醒() {
        when(tenantQuery.activeTenantIds()).thenReturn(List.of(1L));
        when(mapper.selectList(any())).thenReturn(List.of(
                customer(101, 10L), customer(102, 10L), customer(103, 20L)));

        int reminders = newReminder().remindAll();

        assertThat(reminders).isEqualTo(2);
        ArgumentCaptor<MessageSendRequest> captor = ArgumentCaptor.forClass(MessageSendRequest.class);
        verify(messageService, times(2)).send(captor.capture());

        MessageSendRequest first = captor.getAllValues().get(0);
        assertThat(first.templateCode()).isEqualTo(CrmDemoModule.TPL_STAGE_STALE);
        assertThat(first.variables()).containsEntry("count", 2).containsEntry("days", 7);
        assertThat(first.recipients()).containsExactly(RecipientSpec.member(10L));

        MessageSendRequest second = captor.getAllValues().get(1);
        assertThat(second.variables()).containsEntry("count", 1);
        assertThat(second.recipients()).containsExactly(RecipientSpec.member(20L));
    }

    @Test
    void 无超期客户_不发送() {
        when(tenantQuery.activeTenantIds()).thenReturn(List.of(1L));
        when(mapper.selectList(any())).thenReturn(List.of());

        assertThat(newReminder().remindAll()).isZero();
        verifyNoInteractions(messageService);
    }

    @Test
    void 逐租户切换上下文并汇总() {
        when(tenantQuery.activeTenantIds()).thenReturn(List.of(1L, 2L));
        List<Long> seenTenants = new ArrayList<>();
        when(mapper.selectList(any())).thenAnswer(inv -> {
            seenTenants.add(TenantContext.require());
            return List.of(customer(200 + seenTenants.size(), 30L));
        });

        assertThat(newReminder().remindAll()).isEqualTo(2);
        assertThat(seenTenants).containsExactly(1L, 2L);
        assertThat(TenantContext.get()).isNull();   // 扫描结束不残留上下文
    }
}
