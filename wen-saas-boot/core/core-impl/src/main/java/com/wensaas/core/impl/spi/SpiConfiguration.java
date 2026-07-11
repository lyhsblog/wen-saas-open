package com.wensaas.core.impl.spi;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.wensaas.core.impl.spi.module.BusinessModulesProperties;
import com.wensaas.core.impl.spi.module.NamespaceValidator;

/**
 * 业务模块 SPI 子系统配置。
 *
 * <p>各 Registry / Registrar / NamespaceValidator 通过 {@code com.wensaas} 包下的 {@code @Component} 扫描注入；
 * 本类负责显式绑定 {@link BusinessModulesProperties}。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BusinessModulesProperties.class)
public class SpiConfiguration {
}
