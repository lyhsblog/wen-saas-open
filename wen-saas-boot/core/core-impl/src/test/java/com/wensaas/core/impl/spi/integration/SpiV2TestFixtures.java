package com.wensaas.core.impl.spi.integration;

import com.wensaas.core.api.spi.BusinessModuleDescriptor;
import com.wensaas.core.api.spi.ModuleMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import com.wensaas.core.impl.spi.module.BusinessModuleRegistrar;

/**
 * SPI v2 集成测试 fixture：
 * <ul>
 *   <li>schemaCreator 在 datasource 就绪后立刻 CREATE TABLE a_item（早于 BusinessModuleRegistrar 的
 *       SmartInitializingSingleton 阶段），让表前缀校验拿得到 a_item</li>
 *   <li>stub 模块 A（依赖 B）+ 模块 B；A 的 entity 是 {@link AItem}</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class SpiV2TestFixtures {

    @Bean(initMethod = "init")
    public SchemaCreator schemaCreator(JdbcTemplate jdbc) {
        return new SchemaCreator(jdbc);
    }

    @Bean(initMethod = "init")
    public TestRowCleaner testRowCleaner(JdbcTemplate jdbc) {
        // 必须在 BusinessModuleRegistrar (SmartInitializingSingleton) 之前清干净
        // —— 普通 @Bean 的 initMethod 在 singleton 实例化阶段触发，早于 SmartInitializingSingleton
        return new TestRowCleaner(jdbc);
    }

    @Bean
    public BusinessModuleDescriptor moduleA(@Value("${spiv2-test.a-version:1.0.0}") String version) {
        return new BusinessModuleDescriptor() {
            @Override
            public ModuleMetadata metadata() {
                return new ModuleMetadata("a", "Module A", null, version, null, List.of("b"));
            }
        };
    }

    @Bean
    public BusinessModuleDescriptor moduleB() {
        return new BusinessModuleDescriptor() {
            @Override
            public ModuleMetadata metadata() {
                return ModuleMetadata.of("b", "Module B", "1.0.0");
            }
        };
    }

    public static class SchemaCreator {
        private final JdbcTemplate jdbc;

        public SchemaCreator(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public void init() {
            jdbc.execute("CREATE TABLE IF NOT EXISTS a_item (id BIGINT PRIMARY KEY)");
        }
    }

    public static class TestRowCleaner {
        private final JdbcTemplate jdbc;

        public TestRowCleaner(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public void init() {
            jdbc.update("DELETE FROM sys_business_module WHERE code IN (?, ?)", "a", "b");
        }
    }
}
