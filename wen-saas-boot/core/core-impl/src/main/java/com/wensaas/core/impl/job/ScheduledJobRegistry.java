package com.wensaas.core.impl.job;

import com.wensaas.core.api.spi.ScheduledJob;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * jobKey → {@link ScheduledJob} Bean 的运行期索引。
 *
 * <p>JDBC 集群存储下 Quartz 只持久化 jobKey（JobDataMap 不能存 Bean 引用），
 * {@link DelegatingScheduledJob} 触发时经本索引找回执行体。构造期做 jobKey
 * 唯一性校验，重复即启动失败。
 */
public class ScheduledJobRegistry {

    private final Map<String, ScheduledJob> byKey = new LinkedHashMap<>();

    public ScheduledJobRegistry(List<ScheduledJob> jobs) {
        for (ScheduledJob job : jobs) {
            if (byKey.putIfAbsent(job.jobKey(), job) != null) {
                throw new IllegalStateException("ScheduledJob jobKey 重复: " + job.jobKey());
            }
        }
    }

    /** 按 jobKey 取执行体；库里残留孤儿注册项时可能为 null，调用方须兜底。 */
    public ScheduledJob find(String jobKey) {
        return byKey.get(jobKey);
    }

    public Collection<ScheduledJob> all() {
        return byKey.values();
    }
}
