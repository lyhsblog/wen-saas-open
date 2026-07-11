package com.wensaas.core.impl.spi.integration;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SPI v2 集成测试用启动类。com.wensaas 包扫描装配底座所有 @Component / Mapper。
 */
@SpringBootApplication(scanBasePackages = "com.wensaas")
@MapperScan(basePackages = "com.wensaas", annotationClass = Mapper.class)
public class SpiV2TestApp {
}
