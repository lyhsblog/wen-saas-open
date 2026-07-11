package com.wensaas.core.api.ops.setting;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 系统参数读写服务。
 *
 * <p>读：统一 {@code CacheRegion} 缓存（本地 L1 + Redis L2 + pub/sub 跨节点失效），不经业务 MQ。
 * 写：覆盖式批量 update，全部成功才提交，事务边界由 Impl 控制。
 *
 * <p>调用方两类：
 * <ol>
 *   <li>运营 admin 端点（/api/v1/ops/settings/**），按 category 分组读写</li>
 *   <li>底座组件（如 LoginAttemptGuard）通过 {@link #get(SettingCategory, String, Class, Object)} 读单值</li>
 * </ol>
 */
public interface OpsSettingService {

    /**
     * 密钥类参数在 admin 视图中的掩码占位（与租户端存储配置的 secret 回显一致）。
     *
     * <p>契约两条（当前种子无密钥类 key，机制保留供将来新增参数时自动生效）：
     * <ol>
     *   <li>{@link #listByCategory}/{@link #listAll} 对密钥类 key 的非空值一律返回此占位，不出明文</li>
     *   <li>{@link #update} 收到值等于此占位或空白 → 跳过该 key 不覆写（「留空/保持掩码即未修改」）</li>
     * </ol>
     */
    String SECRET_VALUE_MASK = "********";

    /** 密钥类 key 判定：key 名含 secret / access_key / password（大小写不敏感）。 */
    Pattern SECRET_KEY_PATTERN = Pattern.compile("secret|access_key|password", Pattern.CASE_INSENSITIVE);

    /** key 是否密钥类参数（值不回明文、写入走「留空/掩码=不变」契约）。 */
    static boolean isSecretKey(String key) {
        return key != null && SECRET_KEY_PATTERN.matcher(key).find();
    }

    /**
     * 取单值（业务侧使用）。缓存命中直接返；未命中走 DB 查；DB 也无则返回 {@code defaultValue}。
     *
     * @param <T> 反序列化目标类型
     */
    <T> T get(SettingCategory category, String key, Class<T> type, T defaultValue);

    /** 列出某分组下全部参数（运营 admin 视图）。密钥类 key 的非空值以 {@link #SECRET_VALUE_MASK} 回显。 */
    SettingCategoryView listByCategory(SettingCategory category);

    /** 列出全部分组（运营 admin 视图）。密钥类 key 的非空值以 {@link #SECRET_VALUE_MASK} 回显。 */
    List<SettingCategoryView> listAll();

    /**
     * 批量覆盖某分组的 N 个参数；按 {@code (category, key)} 升级写入。
     * 密钥类 key 值为空白或等于 {@link #SECRET_VALUE_MASK} 时跳过不覆写。
     */
    void update(SettingCategory category, Map<String, Object> values, long updatedBy);

    // ===== records =====

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class SettingCategoryView {
        private String category;
        private List<SettingItem> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @ToString
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    static class SettingItem {
        private String key;
        private Object value;
        private String description;
        private LocalDateTime updatedAt;
        private Long updatedBy;
    }
}
