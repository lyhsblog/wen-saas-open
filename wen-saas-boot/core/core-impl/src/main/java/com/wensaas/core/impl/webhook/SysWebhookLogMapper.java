package com.wensaas.core.impl.webhook;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.wensaas.core.impl.webhook.dispatch.WebhookSenderConsumer;

/**
 * {@link SysWebhookLog} CRUD。v1 由 {@link WebhookSenderConsumer} 在每次推送结束后单条 INSERT
 * （单次推送量级远低于操作日志，无需 batch buffer）。
 */
@Mapper
public interface SysWebhookLogMapper extends BaseMapper<SysWebhookLog> {
}
