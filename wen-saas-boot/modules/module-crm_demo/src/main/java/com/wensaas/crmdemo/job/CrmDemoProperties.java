package com.wensaas.crmdemo.job;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * crm_demo 模块配置（业务模块声明自有 {@code @ConfigurationProperties} 的示例）。
 *
 * <p>由 {@link CrmDemoStageStaleReminder} 上的 {@code @EnableConfigurationProperties} 装配，
 * 采用方在 application.yml 以 {@code wen-saas-boot.crm-demo.*} 覆盖默认值。
 */
@ConfigurationProperties(prefix = "wen-saas-boot.crm-demo")
@Getter
@Setter
public class CrmDemoProperties {

    /** 阶段停留超期天数：LEAD / FOLLOWING 客户在当前阶段停留超过该天数未推进，提醒负责人。 */
    private int stageStaleDays = 7;
}
