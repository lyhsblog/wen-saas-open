package com.wensaas.core.impl.spi.module;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

/**
 * 业务模块 schema 迁移器：每个模块独占一条 Flyway 版本线。
 *
 * <p>分模块约定：
 * <ul>
 *   <li>底座迁移在 {@code classpath:db/migration/core}，用默认 history 表 {@code flyway_schema_history}
 *       （Spring Boot 自动配置的 Flyway 负责，启动早期跑）。</li>
 *   <li>每个业务模块的迁移在 {@code classpath:db/migration/<moduleCode>}，本类为其建独立 history 表
 *       {@code flyway_history_<moduleCode>}。模块版本号仅在模块内唯一、从 {@code V1.0.0} 起递增，
 *       与底座 / 其它模块互不影响——彻底消除全局版本撞号与"乱序补跑"问题。</li>
 * </ul>
 *
 * <p>由 {@link BusinessModuleRegistrar} 在拓扑排序之后、表存在性校验（{@link ModuleTableValidator}）
 * 之前调用，保证模块表在被校验/注册前已建好；底座 Flyway 此时已执行完（其 initializer 在 bean
 * 初始化阶段先跑），模块表对底座表的引用安全。
 */
@Component
public class ModuleSchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(ModuleSchemaMigrator.class);
    private static final String BASE_LOCATION = "db/migration/";

    private final DataSource dataSource;
    private final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    public ModuleSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 按给定顺序（拓扑序）为每个有迁移脚本的模块执行独立 Flyway 迁移。 */
    public void migrate(List<String> moduleCodes) {
        for (String code : moduleCodes) {
            String location = BASE_LOCATION + code;
            if (!hasMigrationScripts(location)) {
                continue; // 模块未提供迁移脚本：跳过，不建空 history 表
            }
            String historyTable = "flyway_history_" + code.replace('-', '_');
            int applied = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:" + location)
                    .table(historyTable)
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .validateOnMigrate(true)
                    .load()
                    .migrate()
                    .migrationsExecuted;
            log.info("[SPI] 模块 {} schema 迁移完成（location={}，history={}，本次应用 {} 条）",
                    code, location, historyTable, applied);
        }
    }

    private boolean hasMigrationScripts(String location) {
        try {
            Resource[] res = resourceResolver.getResources("classpath*:" + location + "/*.sql");
            return res.length > 0;
        } catch (Exception e) {
            log.debug("[SPI] 探测模块迁移脚本失败 location={}：{}", location, e.toString());
            return false;
        }
    }
}
