package com.wensaas.core.impl.notification.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * 应用就绪后清空通知模板缓存。
 *
 * <p>背景：seed 通知模板（tenant_id=0 默认文案）通过 Flyway 迁移批量改库时，不会经过运营 UI 的
 * {@link TemplateResolver#evictCodeAllTenants} 失效路径，旧文案会残留至 TTL（默认 1h）过期，期间
 * 通知显示旧占位符/变量名。seed 迁移属低频事件，故选择"重启即全量失效"这一最简方案兜底：迁移随
 * 部署生效，重启随即清空缓存，下次解析即从库重载新文案。
 *
 * <p>失效本身是幂等的全量 SCAN+DEL（见 {@link RedisTemplateResolver#evictAll}），开销可忽略；
 * 失败仅告警、不阻断启动（缓存会随 TTL 自愈）。
 */
public class NotificationTemplateCacheEvictor implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(NotificationTemplateCacheEvictor.class);

    private final TemplateResolver templateResolver;

    public NotificationTemplateCacheEvictor(TemplateResolver templateResolver) {
        this.templateResolver = templateResolver;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            templateResolver.evictAll();
            log.info("[notification] 启动清空通知模板缓存完成（确保 seed 模板迁移即时生效）");
        } catch (RuntimeException e) {
            log.warn("[notification] 启动清空通知模板缓存失败，缓存将随 TTL 自愈", e);
        }
    }
}
