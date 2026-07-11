package com.wensaas.core.impl.file;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * 文件签名下载 URL 缓存。
 *
 * <p>Key: {@code wen-saas-boot:file:url:{fileId}}；Value: 签名 URL；TTL = signedUrlTtl - 1min
 * （留 1 分钟余量，防止刚好返回时已过期）。
 */
public class FileDownloadUrlCache {

    static final String KEY_PREFIX = "wen-saas-boot:file:url:";

    private final StringRedisTemplate redis;
    private final FileProperties props;

    public FileDownloadUrlCache(StringRedisTemplate redis, FileProperties props) {
        this.redis = redis;
        this.props = props;
    }

    public Optional<String> get(String fileId) {
        return Optional.ofNullable(redis.opsForValue().get(key(fileId)));
    }

    public void put(String fileId, String url) {
        Duration ttl = props.getDownloadUrlTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        redis.opsForValue().set(key(fileId), url, ttl);
    }

    public void evict(String fileId) {
        redis.delete(key(fileId));
    }

    static String key(String fileId) {
        return KEY_PREFIX + fileId;
    }
}
