package com.wensaas.core.impl.notification.adapter;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.ChannelAdapter;
import com.wensaas.core.api.notification.send.DeliveryResult;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.RenderedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IM 通道 stub（P1）：微信 / 企微 / 钉钉。v1 仅作占位，日志输出不真发。
 *
 * <p>P1 真实现时改为：依据 OAuth 绑定关系拉外部 ID → 调对应平台 API。
 *
 * <p>v1 默认 {@link #canDeliver} 返回 false（无绑定信息可用），让 MessageService 统一标 SKIPPED。
 * 业务方主动指定 IM 通道时由消息任务的 SKIPPED 状态体现"未触达"，避免悄默接管"全部成功"假象。
 */
public final class ImChannelAdapters {

    private ImChannelAdapters() {}

    public static class WechatChannelAdapter implements ChannelAdapter {
        private static final Logger log = LoggerFactory.getLogger(WechatChannelAdapter.class);
        @Override public Channel channel() { return Channel.WECHAT; }
        @Override public boolean canDeliver(Recipient recipient) { return false; }
        @Override public DeliveryResult deliver(RenderedMessage message, Recipient recipient) {
            log.info("[notify:wechat:stub] userId={} body={}", recipient.userId(), message.body());
            return DeliveryResult.skipped("wechat adapter v1 stub");
        }
    }

    public static class WecomChannelAdapter implements ChannelAdapter {
        private static final Logger log = LoggerFactory.getLogger(WecomChannelAdapter.class);
        @Override public Channel channel() { return Channel.WECOM; }
        @Override public boolean canDeliver(Recipient recipient) { return false; }
        @Override public DeliveryResult deliver(RenderedMessage message, Recipient recipient) {
            log.info("[notify:wecom:stub] userId={} body={}", recipient.userId(), message.body());
            return DeliveryResult.skipped("wecom adapter v1 stub");
        }
    }

    public static class DingtalkChannelAdapter implements ChannelAdapter {
        private static final Logger log = LoggerFactory.getLogger(DingtalkChannelAdapter.class);
        @Override public Channel channel() { return Channel.DINGTALK; }
        @Override public boolean canDeliver(Recipient recipient) { return false; }
        @Override public DeliveryResult deliver(RenderedMessage message, Recipient recipient) {
            log.info("[notify:dingtalk:stub] userId={} body={}", recipient.userId(), message.body());
            return DeliveryResult.skipped("dingtalk adapter v1 stub");
        }
    }
}
