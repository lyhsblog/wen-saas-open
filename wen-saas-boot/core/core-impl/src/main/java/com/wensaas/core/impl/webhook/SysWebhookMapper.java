package com.wensaas.core.impl.webhook;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import com.wensaas.core.impl.webhook.dispatch.WebhookDispatcherConsumer;

/**
 * {@link SysWebhook} CRUD + 订阅匹配查询。
 *
 * <p>{@link #selectActiveByEvent} 走 PG JSONB {@code @>} 操作符命中 GIN 索引
 * （{@code idx_webhook_subscribed_events}）。由 {@link WebhookDispatcherConsumer}
 * 在恢复 TenantContext 后调用，走 MP TenantLine 自动注入 tenant_id。
 */
@Mapper
public interface SysWebhookMapper extends BaseMapper<SysWebhook> {

    List<SysWebhook> selectActiveByEvent(@Param("tenantId") long tenantId,
                                         @Param("eventType") String eventType);

    /**
     * 连续失败计数原子 +1 并返回新值（单条 {@code UPDATE ... RETURNING}）。
     * 多个事件的失败终态并发到达时，读改写会丢计数，必须走原子自增。
     */
    int incrementConsecutiveFailures(@Param("id") long id);

    /**
     * 达阈值自动停用：仅当仍为 active 时置 disabled，返回影响行数。
     * 条件更新保证并发下只有一个调用方"完成停用"（由它发管理员告警，避免重复通知）。
     */
    int disableIfActive(@Param("id") long id);
}
