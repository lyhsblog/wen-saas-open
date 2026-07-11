package com.wensaas.core.impl.spi.module;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 启动期扫描所有 MyBatis-Plus {@link BaseMapper} Bean，反射拿泛型 entity → 读 {@link TableName}
 * 注解，输出 表名 → entity Class 映射。
 *
 * <p>用法：{@link ModuleTableValidator} 判定业务模块表归属与 DB 存在性。
 */
@Component
public class MyBatisEntityTableScanner {

    private static final Logger log = LoggerFactory.getLogger(MyBatisEntityTableScanner.class);

    private final ApplicationContext applicationContext;

    public MyBatisEntityTableScanner(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Map<String, Class<?>> scan() {
        Map<String, Class<?>> tableToEntity = new LinkedHashMap<>();
        Map<String, Class<?>> mapperBeans = collectMapperBeanTypes();
        for (Class<?> mapperType : mapperBeans.values()) {
            Class<?> entityClass = GenericTypeResolver.resolveTypeArgument(mapperType, BaseMapper.class);
            if (entityClass == null) {
                log.debug("[SPI] 跳过 Mapper {}：无法解析 BaseMapper 泛型参数", mapperType.getName());
                continue;
            }
            TableName tableName = entityClass.getAnnotation(TableName.class);
            if (tableName == null || tableName.value() == null || tableName.value().isBlank()) {
                log.debug("[SPI] 跳过 entity {}：没有 @TableName 注解", entityClass.getName());
                continue;
            }
            tableToEntity.putIfAbsent(tableName.value(), entityClass);
        }
        return tableToEntity;
    }

    private Map<String, Class<?>> collectMapperBeanTypes() {
        Map<String, Class<?>> result = new LinkedHashMap<>();
        // 优先走 BeanFactory.getBeanNamesForType(class, true, false)：avoid eager FactoryBean init
        if (applicationContext instanceof ConfigurableApplicationContext cac) {
            ConfigurableListableBeanFactory bf = cac.getBeanFactory();
            for (String name : bf.getBeanNamesForType(BaseMapper.class, true, false)) {
                Class<?> type = bf.getType(name, false);
                if (type != null && BaseMapper.class.isAssignableFrom(type)) {
                    result.putIfAbsent(name, type);
                }
            }
        } else {
            for (String name : applicationContext.getBeanNamesForType(BaseMapper.class)) {
                Class<?> type = applicationContext.getType(name);
                if (type != null && BaseMapper.class.isAssignableFrom(type)) {
                    result.putIfAbsent(name, type);
                }
            }
        }
        Objects.requireNonNull(result);
        return result;
    }
}
