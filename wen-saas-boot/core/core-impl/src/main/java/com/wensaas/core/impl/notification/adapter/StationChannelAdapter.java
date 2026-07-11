package com.wensaas.core.impl.notification.adapter;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.ChannelAdapter;
import com.wensaas.core.api.notification.send.DeliveryResult;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.RenderedMessage;
import com.wensaas.core.impl.notification.inbox.SysStationMessage;
import com.wensaas.core.impl.notification.inbox.SysStationMessageMapper;

import java.time.LocalDateTime;

/**
 * 站内信通道：写一行 {@code sys_station_message}。
 *
 * <p>所有用户必有站内信地址，{@link #canDeliver} 始终 true。
 *
 * <p>title 可空（取自模板 contents）；body 必填，由 MessageService 渲染保证。
 */
public class StationChannelAdapter implements ChannelAdapter {

    private final SysStationMessageMapper mapper;

    public StationChannelAdapter(SysStationMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Channel channel() {
        return Channel.STATION;
    }

    @Override
    public boolean canDeliver(Recipient recipient) {
        return true;
    }

    @Override
    public DeliveryResult deliver(RenderedMessage message, Recipient recipient) {
        SysStationMessage row = new SysStationMessage();
        row.setId(IdWorker.getId());
        row.setTenantId(message.tenantId());
        row.setRecipientId(recipient.userId());
        row.setTaskId(message.taskId());
        row.setCategory(message.category().name());
        row.setSourceModule(message.sourceModule());
        row.setTemplateCode(message.templateCode());
        row.setTitle(message.title());
        row.setBody(message.body());
        row.setLink(message.link());
        row.setRelatedType(message.relatedType());
        row.setRelatedId(message.relatedId());
        row.setPriority(message.priority().name());
        row.setCreatedAt(LocalDateTime.now());
        mapper.insert(row);
        return DeliveryResult.sent();
    }
}
