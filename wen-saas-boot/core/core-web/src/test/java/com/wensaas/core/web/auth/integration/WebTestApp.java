package com.wensaas.core.web.auth.integration;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * core-web 集成测试启动类。
 *
 * <p>Web 容器 + 真 PG + 真 Redis。{@code annotationClass = Mapper.class} 限定只扫
 * {@code @Mapper} 标记的接口，避免与 {@code AuthService} 等普通接口冲突。
 */
@SpringBootApplication(scanBasePackages = "com.wensaas")
@MapperScan(basePackages = "com.wensaas", annotationClass = Mapper.class)
public class WebTestApp {
}
