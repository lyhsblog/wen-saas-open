package com.wensaas.core.impl.openapi.integration;

import com.wensaas.core.impl.openapi.OpenApiProperties;
import com.wensaas.core.impl.openapi.ratelimit.RateLimiter;
import com.wensaas.core.impl.openapi.ratelimit.RedisSlidingWindowRateLimiter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M1 真机验证：rate_limit.lua 经 <b>Redisson 作为唯一 RedisConnectionFactory</b> 的透传是否正确。
 *
 * <p>背景：全仓已用 redisson-spring-boot-starter 替换 Lettuce，{@link StringRedisTemplate} 底层连接
 * 改走 {@link RedissonConnectionFactory}。{@link RedisSlidingWindowRateLimiter} 用
 * {@code StringRedisTemplate.execute(RedisScript, keys, args)} 跑 Lua，依赖 spring-data-redis 的
 * EVAL/EVALSHA 执行器 + 把 Lua 多返回值数组 {@code {allowed, remaining}} 反序列化为 {@code List<Long>}。
 * 本测试用真 Redis 验证这条路径与 Lettuce 行为一致（纯 mock 单测覆盖不到）。
 *
 * <p>不进 CI：需本机已起 Redis，靠环境变量开关启用。
 * <pre>
 *   WEN_REDIS_IT=true [WEN_REDIS_ADDR=redis://127.0.0.1:6379] [WEN_REDIS_PASSWORD=xxx] mvn test -Dtest=RedisLuaPassthroughIntegrationTest ...
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "WEN_REDIS_IT", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisLuaPassthroughIntegrationTest {

    private RedissonClient redisson;
    private RedisSlidingWindowRateLimiter limiter;
    private final long tenantId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);

    @BeforeAll
    void setUp() {
        String addr = System.getenv().getOrDefault("WEN_REDIS_ADDR", "redis://127.0.0.1:6379");
        String password = System.getenv("WEN_REDIS_PASSWORD");
        Config config = new Config();
        config.useSingleServer().setAddress(addr);
        if (password != null && !password.isBlank()) {
            config.useSingleServer().setPassword(password);
        }
        redisson = Redisson.create(config);

        // 生产同款装配：Redisson 作 RedisConnectionFactory，StringRedisTemplate 用法不变。
        RedissonConnectionFactory cf = new RedissonConnectionFactory(redisson);
        StringRedisTemplate template = new StringRedisTemplate(cf);
        template.afterPropertiesSet();

        OpenApiProperties props = new OpenApiProperties();
        props.getRateLimit().setEnabled(true);
        props.getRateLimit().setDefaultLimit(3);                 // 小窗口便于打满
        props.getRateLimit().setWindow(Duration.ofSeconds(60));
        limiter = new RedisSlidingWindowRateLimiter(template, props);
    }

    @AfterAll
    void tearDown() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    @Test
    void luaScript_multiReturnValue_deserializesThroughRedisson() {
        // limit=3：前 3 次放行且 remaining 递减，第 4 次拒绝。
        // 若 Redisson 透传 / List<Long> 反序列化有问题，这里会拿到 null / 类型异常 / 错误计数。
        RateLimiter.Decision d1 = limiter.tryAcquire(tenantId, null);
        RateLimiter.Decision d2 = limiter.tryAcquire(tenantId, null);
        RateLimiter.Decision d3 = limiter.tryAcquire(tenantId, null);
        RateLimiter.Decision d4 = limiter.tryAcquire(tenantId, null);

        assertThat(d1.allowed()).isTrue();
        assertThat(d1.remaining()).isEqualTo(2);
        assertThat(d2.allowed()).isTrue();
        assertThat(d2.remaining()).isEqualTo(1);
        assertThat(d3.allowed()).isTrue();
        assertThat(d3.remaining()).isEqualTo(0);

        assertThat(d4.allowed()).isFalse();
        assertThat(d4.remaining()).isEqualTo(0);

        // limit 字段正确回填（与配置一致），确认走的是 Lua 分支而非降级放行
        assertThat(d4.limit()).isEqualTo(3);
    }
}
