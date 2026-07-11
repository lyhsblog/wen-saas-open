package com.wensaas.core.impl.imports;

import com.alibaba.fastjson2.JSON;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * 预览暂存（Redis 实现）。
 *
 * <p>key：{@code wen-saas-boot:import:session:{sessionId}}，value = JSON({@link ImportSession})，TTL 30min。
 *
 * <p>设计说明：原始上传文件存文件中心（{@link ImportSession#fileId}），Redis 只暂存会话元数据
 * + 轻量预览（错误 / 通过各 ≤200 行）；错误明细下载与确认执行按 fileId 重读原件重校验，
 * 不在 Redis 堆全量行（避免多 MB 大 value）。
 *
 * <p>由 {@link ImportConfiguration} 装配（与任务中心同条件）。
 */
public class ImportSessionStore {

    static final String PREFIX = "wen-saas-boot:import:session:";
    static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    public ImportSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void save(ImportSession session) {
        redis.opsForValue().set(key(session.sessionId()), JSON.toJSONString(session), TTL);
    }

    public Optional<ImportSession> load(String sessionId) {
        String raw = redis.opsForValue().get(key(sessionId));
        return raw == null ? Optional.empty() : Optional.of(JSON.parseObject(raw, ImportSession.class));
    }

    public void delete(String sessionId) {
        redis.delete(key(sessionId));
    }

    private static String key(String sessionId) {
        return PREFIX + sessionId;
    }
}
