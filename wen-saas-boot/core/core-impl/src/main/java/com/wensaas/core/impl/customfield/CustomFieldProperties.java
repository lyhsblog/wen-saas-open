package com.wensaas.core.impl.customfield;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * 自定义字段框架配置。前缀 {@code wen-saas-boot.customfield}。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.customfield")
@Getter
@Setter
public class CustomFieldProperties {

    /** 是否启用自定义字段框架（默认 true）。 */
    private boolean enabled = true;

    /** Redis 缓存 TTL。默认 1 小时。 */
    private Duration cacheTtl = Duration.ofHours(1);

    /** 字段基础额度（每租户）。 */
    private int defaultFieldQuota = 20;

    /** 索引基础额度（每租户）。 */
    private int defaultIndexQuota = 5;

    /**
     * v2 MyBatis 拦截器（写入校验 + 查询翻译）开关。默认 true。
     * <p>关闭时业务方需主动调 {@code CustomFieldService.validateValues} 校验；
     * 查询路径无翻译能力（业务方需手写 {@code extra->>'cf_xxx'}）。
     */
    private boolean interceptorEnabled = true;

}
