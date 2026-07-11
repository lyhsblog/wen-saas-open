package com.wensaas.core.impl.job;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置定时任务（内嵌 Quartz）配置。
 *
 * <pre>
 * wen-saas-boot:
 *   jobs:
 *     enabled: true              # 总开关；false 时不注册任何调度（Job Bean 仍装配）
 *     cron:                      # 按 jobKey 覆盖默认 cron（默认值在各 Job 代码内）
 *       tenant-expire: "0 5 0 * * ?"
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.jobs")
@Getter
@Setter
public class JobProperties {

    private boolean enabled = true;

    /** jobKey → cron 覆盖；未配置的任务用 {@code ScheduledJob.cron()} 默认值。 */
    private Map<String, String> cron = new LinkedHashMap<>();
}
