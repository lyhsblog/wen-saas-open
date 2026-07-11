package com.wensaas.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动完成后追加一行带版本 / profile / 端口 / 耗时的应用横幅。
 *
 * <p>Spring Boot 框架默认的 {@code Started ...} / {@code Starting ...} 两行保留不动（那是
 * {@code StartupInfoLogger} 硬编码、措辞不可配）；此处只是 {@link ApplicationReadyEvent}
 * 就绪后再补一句自定义信息。版本号取 {@link BuildProperties}（由 spring-boot-maven-plugin
 * 的 build-info 目标生成，IDE 未跑该目标时回退 {@code dev}）。
 */
@Component
public class StartupBannerListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupBannerListener.class);

    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildProperties;

    public StartupBannerListener(Environment environment, ObjectProvider<BuildProperties> buildProperties) {
        this.environment = environment;
        this.buildProperties = buildProperties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build != null ? build.getVersion() : "dev";
        String[] active = environment.getActiveProfiles();
        String profiles = String.join(",", active.length > 0 ? active : environment.getDefaultProfiles());
        String seconds = String.format("%.3f", event.getTimeTaken().toMillis() / 1000.0);

        log.info("Wen SaaS Boot v{} 已就绪 · profile={} · 端口 {} · 启动耗时 {} 秒",
                version, profiles, resolvePort(event.getApplicationContext()), seconds);
    }

    /** 取实际绑定端口（Web 应用）；非 Web 或取不到返回 -1。 */
    private int resolvePort(ApplicationContext ctx) {
        if (ctx instanceof WebServerApplicationContext wsac && wsac.getWebServer() != null) {
            return wsac.getWebServer().getPort();
        }
        return -1;
    }
}
