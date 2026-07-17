package com.wensaas.core.impl.spi.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wensaas.core.api.spi.ModuleMetadata;
import com.wensaas.core.impl.spi.module.BusinessModuleDbSynchronizer;
import com.wensaas.core.impl.spi.module.BusinessModuleRegistrar;
import com.wensaas.core.impl.spi.module.BusinessModuleRegistry;
import com.wensaas.core.impl.spi.module.SysBusinessModule;
import com.wensaas.core.impl.spi.module.SysBusinessModuleMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPI v2 端到端集成测试（真 PostgreSQL + 真 MyBatis-Plus）。
 *
 * <p>验证：
 * <ul>
 *   <li>注册顺序按拓扑序：b → a（a 依赖 b）</li>
 *   <li>sys_business_module 表写入完整：a 和 b 两行</li>
 *   <li>再次触发同步且 version 提升时执行 UPDATE</li>
 *   <li>预置套餐 a.starter 落库上架（source_module=a、模块 = 声明 b ∪ 自身 a），二次同步幂等</li>
 * </ul>
 */
@SpringBootTest(classes = SpiV2TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(SpiV2TestFixtures.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BusinessModuleSpiV2IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SysBusinessModuleMapper sysModuleMapper;

    @Autowired
    BusinessModuleRegistry moduleRegistry;

    @Autowired
    BusinessModuleRegistrar registrar;

    @Autowired
    BusinessModuleDbSynchronizer dbSynchronizer;

    @Autowired
    com.wensaas.core.impl.pkg.PackageSpiSynchronizer packageSpiSynchronizer;

    @Autowired
    org.springframework.context.ApplicationContext applicationContext;

    @AfterAll
    void cleanupAfter() {
        jdbc.update("DELETE FROM sys_business_module WHERE code IN (?, ?)", "a", "b");
        jdbc.update("DELETE FROM sys_package_module WHERE package_id IN "
                + "(SELECT id FROM sys_package WHERE code = ?)", "a.starter");
        jdbc.update("DELETE FROM sys_package WHERE code = ?", "a.starter");
        jdbc.execute("DROP TABLE IF EXISTS a_item");
    }

    @Test
    @Order(1)
    void registersInTopologicalOrder() {
        List<String> codes = moduleRegistry.getAll().stream().map(ModuleMetadata::code).toList();
        assertThat(codes).contains("a", "b");
        // b 必须早于 a（a 依赖 b）
        assertThat(codes.indexOf("b")).isLessThan(codes.indexOf("a"));
    }

    @Test
    @Order(2)
    void writesToSysBusinessModuleTable() {
        SysBusinessModule a = sysModuleMapper.selectOne(
                new LambdaQueryWrapper<SysBusinessModule>().eq(SysBusinessModule::getCode, "a"));
        SysBusinessModule b = sysModuleMapper.selectOne(
                new LambdaQueryWrapper<SysBusinessModule>().eq(SysBusinessModule::getCode, "b"));

        assertThat(a).isNotNull();
        assertThat(a.getVersion()).isEqualTo("1.0.0");
        assertThat(b).isNotNull();
        assertThat(b.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    @Order(3)
    void syncAgainWithUpgradedVersion_updatesRow() {
        // 直接调用 synchronizer 模拟"新版本部署再次启动"
        dbSynchronizer.sync(List.of(new com.wensaas.core.api.spi.BusinessModuleDescriptor() {
            @Override
            public ModuleMetadata metadata() {
                return ModuleMetadata.of("a", "Module A v2", "1.2.0");
            }
        }));

        SysBusinessModule a = sysModuleMapper.selectOne(
                new LambdaQueryWrapper<SysBusinessModule>().eq(SysBusinessModule::getCode, "a"));
        assertThat(a.getVersion()).isEqualTo("1.2.0");
        assertThat(a.getName()).isEqualTo("Module A v2");
    }

    @Test
    @Order(4)
    void declaredPackage_landedActiveWithSelfIncluded() {
        var pkg = jdbc.queryForMap(
                "SELECT status, is_system, source_module, default_ttl_days FROM sys_package WHERE code = ?",
                "a.starter");
        assertThat(((Number) pkg.get("status")).intValue()).isEqualTo(1);
        assertThat(pkg.get("is_system")).isEqualTo(false);
        assertThat(pkg.get("source_module")).isEqualTo("a");
        assertThat(pkg.get("default_ttl_days")).isNull();

        List<String> modules = jdbc.queryForList(
                "SELECT module_code FROM sys_package_module WHERE package_id = "
                        + "(SELECT id FROM sys_package WHERE code = ?) ORDER BY module_code",
                String.class, "a.starter");
        // 声明只写了 b，自身 a 由同步器隐含并入
        assertThat(modules).containsExactly("a", "b");
    }

    @Test
    @Order(5)
    void syncAgain_isIdempotent_andPreservesOpsEdits() {
        // 模拟运营改名后再次重启：整行不动（insert-if-missing）
        jdbc.update("UPDATE sys_package SET name = ? WHERE code = ?", "运营改过的名字", "a.starter");

        packageSpiSynchronizer.sync(List.copyOf(
                applicationContext.getBeansOfType(
                        com.wensaas.core.api.spi.BusinessModuleDescriptor.class).values()));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sys_package WHERE code = ?", Integer.class, "a.starter"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT name FROM sys_package WHERE code = ?", String.class, "a.starter"))
                .isEqualTo("运营改过的名字");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sys_package_module WHERE package_id = "
                        + "(SELECT id FROM sys_package WHERE code = ?)", Integer.class, "a.starter"))
                .isEqualTo(2);
    }
}
