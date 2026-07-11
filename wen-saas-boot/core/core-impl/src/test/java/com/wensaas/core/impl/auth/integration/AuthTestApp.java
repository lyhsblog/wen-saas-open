package com.wensaas.core.impl.auth.integration;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Auth 集成测试启动类。
 *
 * <p>{@code annotationClass = Mapper.class}：限定只扫描标注了 {@code @Mapper} 的接口，
 * 避免把 {@code AuthService} 等普通业务接口当成 Mapper（同包内）。
 */
@SpringBootApplication(scanBasePackages = "com.wensaas")
@MapperScan(basePackages = "com.wensaas", annotationClass = Mapper.class)
public class AuthTestApp {
}
