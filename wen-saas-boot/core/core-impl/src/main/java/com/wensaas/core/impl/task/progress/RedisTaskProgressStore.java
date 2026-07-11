package com.wensaas.core.impl.task.progress;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import com.wensaas.core.impl.task.TaskProperties;

/**
 * Redis 实现：
 * <ul>
 *   <li>进度 key：{@code wen-saas-boot:task:progress:{taskId}}，value = JSON({@link TaskProgress})</li>
 *   <li>取消标志 key：{@code wen-saas-boot:task:cancel:{taskId}}，value = "1"</li>
 * </ul>
 */
public class RedisTaskProgressStore implements TaskProgressStore {

    static final String PROGRESS_PREFIX = "wen-saas-boot:task:progress:";
    static final String CANCEL_PREFIX = "wen-saas-boot:task:cancel:";

    private final StringRedisTemplate redis;
    private final TaskProperties props;

    public RedisTaskProgressStore(StringRedisTemplate redis, TaskProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    public void writeProgress(long taskId, TaskProgress progress) {
        redis.opsForValue().set(progressKey(taskId), serialize(progress), props.getProgressKeyTtl());
    }

    @Override
    public Optional<TaskProgress> readProgress(long taskId) {
        String raw = redis.opsForValue().get(progressKey(taskId));
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(raw));
    }

    @Override
    public void writeTerminalProgress(long taskId, TaskProgress progress) {
        redis.opsForValue().set(progressKey(taskId), serialize(progress), props.getProgressKeyTtl());
    }

    @Override
    public void deleteProgress(long taskId) {
        redis.delete(progressKey(taskId));
    }

    @Override
    public void markCancelled(long taskId) {
        redis.opsForValue().set(cancelKey(taskId), "1", props.getCancelFlagTtl());
    }

    @Override
    public boolean isCancelled(long taskId) {
        String raw = redis.opsForValue().get(cancelKey(taskId));
        return "1".equals(raw);
    }

    @Override
    public void clearCancelled(long taskId) {
        redis.delete(cancelKey(taskId));
    }

    static String progressKey(long taskId) {
        return PROGRESS_PREFIX + taskId;
    }

    static String cancelKey(long taskId) {
        return CANCEL_PREFIX + taskId;
    }

    static String serialize(TaskProgress p) {
        return JSON.toJSONString(p, JSONWriter.Feature.WriteNulls);
    }

    static TaskProgress deserialize(String json) {
        try {
            return JSON.parseObject(json, TaskProgress.class, JSONReader.Feature.SupportSmartMatch);
        } catch (RuntimeException e) {
            throw new IllegalStateException("TaskProgress JSON 反序列化失败：" + json, e);
        }
    }
}
