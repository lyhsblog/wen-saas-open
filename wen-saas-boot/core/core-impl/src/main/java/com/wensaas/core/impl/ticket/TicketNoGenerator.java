package com.wensaas.core.impl.ticket;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 工单号生成：{@code T-YYYYMMDD-NNNN}。
 *
 * <p>按自然日用 Redis INCR 计数，全局唯一、可读；计数键 2 天过期。
 */
@Component
public class TicketNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KEY_PREFIX = "ticket:no:seq:";

    private final StringRedisTemplate redis;

    public TicketNoGenerator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String next() {
        String day = LocalDate.now().format(DAY);
        String key = KEY_PREFIX + day;
        Long seq = redis.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            redis.expire(key, Duration.ofDays(2));
        }
        long n = seq == null ? System.currentTimeMillis() % 10000 : seq;
        return String.format("T-%s-%04d", day, n);
    }
}
