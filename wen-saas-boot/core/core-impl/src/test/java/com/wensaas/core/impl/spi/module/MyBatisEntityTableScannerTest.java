package com.wensaas.core.impl.spi.module;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 纯粹的单测：用 mock {@link ConfigurableApplicationContext} 驱动 scanner，不启动任何 Spring 容器，
 * 避免被外部 ComponentScan 误扫到测试内部类。
 */
class MyBatisEntityTableScannerTest {

    @Test
    void scansAnnotatedEntityFromMapperBean() {
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        ConfigurableListableBeanFactory bf = mock(ConfigurableListableBeanFactory.class);
        when(ctx.getBeanFactory()).thenReturn(bf);
        when(bf.getBeanNamesForType(BaseMapper.class, true, false)).thenReturn(new String[]{"scannerTestEntityMapper"});
        when(bf.getType(eq("scannerTestEntityMapper"), eq(false))).thenAnswer(inv -> ScannerTestEntityMapper.class);

        MyBatisEntityTableScanner scanner = new MyBatisEntityTableScanner(ctx);
        Map<String, Class<?>> result = scanner.scan();

        assertThat(result).containsEntry("scanner_test_entity", ScannerTestEntity.class);
    }

    @Test
    void skipsMapperWhoseEntityHasNoTableNameAnnotation() {
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        ConfigurableListableBeanFactory bf = mock(ConfigurableListableBeanFactory.class);
        when(ctx.getBeanFactory()).thenReturn(bf);
        when(bf.getBeanNamesForType(BaseMapper.class, true, false)).thenReturn(new String[]{"pojoWithoutTableMapper"});
        when(bf.getType(eq("pojoWithoutTableMapper"), eq(false))).thenAnswer(inv -> PojoWithoutTableMapper.class);

        MyBatisEntityTableScanner scanner = new MyBatisEntityTableScanner(ctx);
        Map<String, Class<?>> result = scanner.scan();

        assertThat(result.values()).doesNotContain(PojoWithoutTable.class);
    }

    @TableName("scanner_test_entity")
    static class ScannerTestEntity {
        Long id;
    }

    static class PojoWithoutTable {
        Long id;
    }

    interface ScannerTestEntityMapper extends BaseMapper<ScannerTestEntity> {
    }

    interface PojoWithoutTableMapper extends BaseMapper<PojoWithoutTable> {
    }
}
