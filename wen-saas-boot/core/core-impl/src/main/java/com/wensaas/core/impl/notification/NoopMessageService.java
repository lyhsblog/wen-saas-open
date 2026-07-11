package com.wensaas.core.impl.notification;

import com.wensaas.core.api.notification.send.MessageSendRequest;
import com.wensaas.core.api.notification.send.MessageService;
import com.wensaas.core.api.notification.send.MessageTaskId;
import com.wensaas.core.api.notification.send.MessageTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * {@code wen-saas-boot.notification.enabled=false} 时的兜底实现：
 * send 仅打日志返回 noop taskId，status 返回 empty。
 */
public class NoopMessageService implements MessageService {

    private static final Logger log = LoggerFactory.getLogger(NoopMessageService.class);

    @Override
    public MessageTaskId send(MessageSendRequest request) {
        log.info("[notify:noop] notification disabled, drop send template={} recipients={}",
                request.templateCode(), request.recipients().size());
        return new MessageTaskId("noop-disabled");
    }

    @Override
    public Optional<MessageTaskStatus> status(MessageTaskId taskId) {
        return Optional.empty();
    }
}
