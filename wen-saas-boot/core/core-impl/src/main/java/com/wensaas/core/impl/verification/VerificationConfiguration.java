package com.wensaas.core.impl.verification;

import com.wensaas.core.api.notification.send.Channel;
import com.wensaas.core.api.notification.send.ChannelAdapter;
import com.wensaas.core.api.verification.VerificationCodeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

/**
 * 验证码（OTP）能力装配。依赖通知底座的 {@code channelAdapterMap}（同步下发），
 * 故与 {@link com.wensaas.core.impl.notification.NotificationConfiguration} 同条件（notification.enabled）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VerificationCodeProperties.class)
@ConditionalOnProperty(prefix = "wen-saas-boot.notification", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class VerificationConfiguration {

    @Bean
    @ConditionalOnMissingBean(VerificationCodeService.class)
    public VerificationCodeService verificationCodeService(StringRedisTemplate redis,
                                                           Map<Channel, ChannelAdapter> channelAdapterMap,
                                                           VerificationCodeProperties props) {
        return new VerificationCodeServiceImpl(redis, channelAdapterMap, props);
    }
}
