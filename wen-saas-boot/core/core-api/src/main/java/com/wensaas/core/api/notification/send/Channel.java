package com.wensaas.core.api.notification.send;

/**
 * 通知通道。
 *
 * <p>v1 P0：{@link #STATION} 真实现 + {@link #EMAIL} 走 Spring Mail（dev/test 默认 noop）。
 * v1 P1（stub 占位）：{@link #SMS} / {@link #WECHAT} / {@link #WECOM} / {@link #DINGTALK}。
 */
public enum Channel {
    STATION,
    EMAIL,
    SMS,
    WECHAT,
    WECOM,
    DINGTALK;

    /** 是否归属"IM"通道分组（共用一个 RocketMQ topic：wen_notify_im）。 */
    public boolean isIm() {
        return this == WECHAT || this == WECOM || this == DINGTALK;
    }
}
