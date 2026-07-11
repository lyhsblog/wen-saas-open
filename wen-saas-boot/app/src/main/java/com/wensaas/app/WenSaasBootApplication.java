package com.wensaas.app;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Wen SaaS Boot 启动入口。
 *
 * <p>底座与业务模块通过 Spring Bean 扫描 + BusinessModuleDescriptor SPI 注册，
 * 启动时由底座完成命名空间冲突校验与扩展点注册。
 *
 * <p>Mapper 扫描统一规则：basePackages = {@code com.wensaas}，{@code annotationClass = Mapper}
 * —— 仅扫描带 {@code @Mapper} 注解的接口，避免把 Service / Provider 等普通接口误扫成 Mapper Bean。
 */
@SpringBootApplication(scanBasePackages = "com.wensaas")
@MapperScan(basePackages = "com.wensaas", annotationClass = Mapper.class)
public class WenSaasBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(WenSaasBootApplication.class, args);
    }
}
