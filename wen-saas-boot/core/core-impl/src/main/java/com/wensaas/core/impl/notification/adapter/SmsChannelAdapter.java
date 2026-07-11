package com.wensaas.core.impl.notification.adapter;

import com.alibaba.fastjson2.JSON;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.ChannelAdapter;
import com.wensaas.core.api.notification.send.DeliveryResult;
import com.wensaas.core.api.notification.send.Recipient;
import com.wensaas.core.api.notification.template.RenderedMessage;
import com.wensaas.core.impl.notification.SmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 短信通道（阿里云短信）。
 *
 * <ul>
 *   <li>{@code wen-saas-boot.sms.noop=true}（凭据未就绪时默认）：仅打日志返回 SENT，不真发。</li>
 *   <li>{@code noop=false}：调阿里云 SendSms。阿里云走"审核模板 + 参数"制，故用
 *       {@link SmsProperties#getTemplateMapping()} 把内部 templateCode 映射到阿里云 templateCode，
 *       {@link RenderedMessage#variables()} 序列化为 templateParam JSON。</li>
 * </ul>
 */
public class SmsChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(SmsChannelAdapter.class);

    private final SmsProperties props;
    private volatile Client client;

    public SmsChannelAdapter(SmsProperties props) {
        this.props = props;
    }

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public boolean canDeliver(Recipient recipient) {
        return recipient.phone() != null && !recipient.phone().isBlank();
    }

    @Override
    public DeliveryResult deliver(RenderedMessage message, Recipient recipient) {
        if (props.isNoop()) {
            log.info("[notify:sms:noop] to={} body={}", recipient.phone(), message.body());
            return DeliveryResult.sent();
        }
        String aliyunTemplate = props.getTemplateMapping().get(message.templateCode());
        if (aliyunTemplate == null || aliyunTemplate.isBlank()) {
            return DeliveryResult.failed("无阿里云模板映射: " + message.templateCode());
        }
        try {
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(recipient.phone())
                    .setSignName(props.getSignName())
                    .setTemplateCode(aliyunTemplate)
                    .setTemplateParam(JSON.toJSONString(message.variables()));
            SendSmsResponse response = client().sendSms(request);
            String code = response.getBody() == null ? null : response.getBody().getCode();
            if ("OK".equals(code)) {
                return DeliveryResult.sent();
            }
            String reason = response.getBody() == null ? "无响应体"
                    : code + ":" + response.getBody().getMessage();
            log.warn("[notify:sms] 发送失败 to={} reason={}", recipient.phone(), reason);
            return DeliveryResult.failed(reason);
        } catch (Exception ex) {
            log.warn("[notify:sms] 发送异常 to={} err={}", recipient.phone(), ex.toString());
            return DeliveryResult.failed(ex.getMessage());
        }
    }

    private Client client() throws Exception {
        Client c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    Config config = new Config()
                            .setAccessKeyId(props.getAccessKeyId())
                            .setAccessKeySecret(props.getAccessKeySecret())
                            .setEndpoint(props.getEndpoint());
                    c = new Client(config);
                    client = c;
                }
            }
        }
        return c;
    }
}
