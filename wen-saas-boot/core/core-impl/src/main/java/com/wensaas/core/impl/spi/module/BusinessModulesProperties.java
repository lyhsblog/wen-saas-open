package com.wensaas.core.impl.spi.module;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import com.wensaas.core.impl.spi.SpiConfiguration;

/**
 * 业务模块开关配置。
 *
 * <pre>
 * wen-saas-boot:
 *   business-modules:
 *     enabled:
 *       crm: true
 *       approval: true
 *       ticket: false
 * </pre>
 *
 * 默认未列出的模块视为 enabled = true；显式置 false 才跳过注册。
 *
 * <p>通过 {@link SpiConfiguration} 上的 {@code @EnableConfigurationProperties} 绑定为 Bean。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.business-modules")
@Getter
@Setter
public class BusinessModulesProperties {

    private Map<String, Boolean> enabled = new HashMap<>();

    public void setEnabled(Map<String, Boolean> enabled) {
        this.enabled = enabled == null ? new HashMap<>() : enabled;
    }

    public boolean isModuleEnabled(String code) {
        return !Boolean.FALSE.equals(enabled.get(code));
    }
}
