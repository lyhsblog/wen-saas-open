package com.wensaas.core.impl.notification.template;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import com.wensaas.core.impl.notification.NotificationProperties;

/**
 * Redis 缓存版 {@link TemplateResolver}。
 *
 * <p>Key 形如 {@code wen-saas-boot:notify:tpl:{tenantId}:{templateCode}}，
 * Value 为 fastjson2 JSON。TTL 由 {@link NotificationProperties#getTemplateCacheTtl()} 控制（默认 1h）。
 *
 * <p>缓存 miss 时调 {@link TemplateDataLoader} 装载并写回；模板不存在亦缓存"空标记"
 * {@code "__NULL__"} 防穿透。
 */
public class RedisTemplateResolver implements TemplateResolver {

    static final String KEY_PREFIX = "wen-saas-boot:notify:tpl:";
    private static final String NULL_MARKER = "\"__NULL__\"";

    private final StringRedisTemplate redis;
    private final TemplateDataLoader loader;
    private final NotificationProperties props;

    public RedisTemplateResolver(StringRedisTemplate redis,
                                  TemplateDataLoader loader,
                                  NotificationProperties props) {
        this.redis = redis;
        this.loader = loader;
        this.props = props;
    }

    @Override
    public Optional<TemplateSnapshot> resolve(long tenantId, String templateCode) {
        String key = keyOf(tenantId, templateCode);
        String raw = redis.opsForValue().get(key);
        if (raw != null) {
            if (NULL_MARKER.equals(raw)) {
                return Optional.empty();
            }
            return Optional.of(deserialize(raw));
        }
        Optional<TemplateSnapshot> loaded = loader.load(tenantId, templateCode);
        if (loaded.isEmpty()) {
            redis.opsForValue().set(key, NULL_MARKER, props.getTemplateCacheTtl());
            return Optional.empty();
        }
        redis.opsForValue().set(key, serialize(loaded.get()), props.getTemplateCacheTtl());
        return loaded;
    }

    @Override
    public void evict(long tenantId, String templateCode) {
        redis.delete(keyOf(tenantId, templateCode));
    }

    @Override
    public void evictCodeAllTenants(String templateCode) {
        // key 形如 notify:tpl:{tenantId}:{templateCode}；templateCode 仅含 [a-z0-9_.]，glob 中均为字面量
        ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + "*:" + templateCode).count(500).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                redis.delete(cursor.next());
            }
        }
    }

    @Override
    public void evictAll() {
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                redis.delete(cursor.next());
            }
        }
    }

    static String keyOf(long tenantId, String templateCode) {
        return KEY_PREFIX + tenantId + ":" + templateCode;
    }

    static String serialize(TemplateSnapshot snap) {
        return JSON.toJSONString(snap,
                JSONWriter.Feature.WriteNulls,
                JSONWriter.Feature.WriteEnumUsingToString);
    }

    static TemplateSnapshot deserialize(String json) {
        try {
            return JSON.parseObject(json, TemplateSnapshot.class,
                    JSONReader.Feature.SupportSmartMatch);
        } catch (RuntimeException e) {
            throw new IllegalStateException("TemplateSnapshot JSON 反序列化失败：" + json, e);
        }
    }
}
