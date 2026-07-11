package com.wensaas.core.impl.tenant.integration;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * core-impl 集成测试用启动类（替代 app 模块的 main 类，core-impl 不应反向依赖 app）。
 *
 * <p>Mapper 扫描与生产 App 一致：annotationClass = Mapper 限定，避免把 AuthService 等接口误注册。
 */
@SpringBootApplication(scanBasePackages = "com.wensaas")
@MapperScan(basePackages = "com.wensaas", annotationClass = Mapper.class)
public class TestApp {
}
