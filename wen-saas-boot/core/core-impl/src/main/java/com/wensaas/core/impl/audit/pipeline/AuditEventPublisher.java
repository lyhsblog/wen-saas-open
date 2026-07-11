package com.wensaas.core.impl.audit.pipeline;

import com.wensaas.core.api.audit.AuditEvent;
import com.wensaas.core.api.audit.LoginAuditEvent;

/**
 * 操作日志 / 登录日志事件发布接口。
 *
 * <p>v1 默认实现走 RocketMQ asyncSend；测试可注入 in-memory 实现。
 */
public interface AuditEventPublisher {

    void publish(AuditEvent event);

    void publish(LoginAuditEvent event);
}
