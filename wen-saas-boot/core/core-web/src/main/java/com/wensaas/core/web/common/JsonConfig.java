package com.wensaas.core.web.common;

import com.wensaas.core.impl.notification.access.RelatedObjectAccessEvaluator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局配置。
 *
 * <ul>
 *   <li>雪花 {@code Long}/{@code long} → JSON string（超出 JS safe integer 2^53）</li>
 *   <li>{@code OffsetDateTime} → ISO-8601 {@code 2026-05-22T18:30:00+08:00}</li>
 *   <li>{@code LocalDateTime} → 按 {@code Asia/Shanghai} 转 ISO-8601 +08:00（DB 存 TIMESTAMP 无时区，按北京时间存）</li>
 *   <li>{@code @RelatedObjectGuarded} DTO → 序列化期关联对象权限探针；
 *   notification 模块关闭 / evaluator 缺失时不挂修饰器</li>
 * </ul>
 *
 * <p>Spring Boot 4 使用 Jackson 3.x（{@code tools.jackson.*}）；定制器接口是
 * {@link JsonMapperBuilderCustomizer}，序列化基类是 {@link ValueSerializer}。
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfig {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OFFSET_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Bean
    public JsonMapperBuilderCustomizer wenSaasBootJacksonCustomizer(
            ObjectProvider<RelatedObjectAccessEvaluator> evaluatorProvider) {
        return builder -> {
            SimpleModule module = new SimpleModule("wen-saas-boot");
            ValueSerializer<Long> longToStr = new ValueSerializer<>() {
                @Override
                public void serialize(Long value, JsonGenerator gen, SerializationContext ctx) {
                    gen.writeString(String.valueOf(value));
                }
            };
            module.addSerializer(Long.class, longToStr);
            module.addSerializer(Long.TYPE, longToStr);
            module.addSerializer(OffsetDateTime.class, new ValueSerializer<>() {
                @Override
                public void serialize(OffsetDateTime value, JsonGenerator gen, SerializationContext ctx) {
                    gen.writeString(value.format(OFFSET_FMT));
                }
            });
            module.addSerializer(LocalDateTime.class, new ValueSerializer<>() {
                @Override
                public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctx) {
                    gen.writeString(value.atZone(SHANGHAI).toOffsetDateTime().format(OFFSET_FMT));
                }
            });
            // 关联对象权限探针：notification 模块开启时才挂修饰器（evaluator 缺失则跳过，不阻塞 JSON 启动）
            RelatedObjectAccessEvaluator evaluator = evaluatorProvider.getIfAvailable();
            if (evaluator != null) {
                module.setSerializerModifier(new RelatedObjectSerializerModifier(evaluator));
            }
            builder.addModule(module);
        };
    }
}
