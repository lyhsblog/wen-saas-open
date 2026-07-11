package com.wensaas.core.impl.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import com.wensaas.core.impl.notification.adapter.SmsChannelAdapter;

/**
 * 短信通道配置（阿里云短信）。
 *
 * <pre>
 * wen-saas-boot:
 *   sms:
 *     noop: true                       # 凭据未就绪时 true：SmsChannelAdapter 仅打日志不真发
 *     access-key-id: ${ALIYUN_SMS_AK:}
 *     access-key-secret: ${ALIYUN_SMS_SK:}
 *     sign-name: Wen SaaS
 *     endpoint: dysmsapi.aliyuncs.com
 *     template-mapping:                # 内部 templateCode -> 阿里云已审核 templateCode
 *       auth.otp: SMS_xxxxxxx
 * </pre>
 *
 * <p>阿里云短信走"审核签名 + 审核模板 + 变量参数"制，不支持自由文本，故 {@link Map} 把内部
 * templateCode 映射到阿里云 templateCode，渲染变量作为 templateParam JSON 下发。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.sms")
@Getter
@Setter
public class SmsProperties {

    /** true：不真发，仅 INFO 日志（dev/test 默认；prod 凭据到位后改 false）。 */
    private boolean noop = true;
    private String accessKeyId;
    private String accessKeySecret;
    private String signName;
    private String endpoint = "dysmsapi.aliyuncs.com";
    /** 内部 templateCode -> 阿里云 templateCode。 */
    private Map<String, String> templateMapping = new HashMap<>();

}
