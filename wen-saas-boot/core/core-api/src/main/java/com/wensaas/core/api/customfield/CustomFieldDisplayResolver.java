package com.wensaas.core.api.customfield;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 自定义字段展示视图填充 SPI：把业务 entity / extra Map 中的自定义字段值，附加翻译后的
 * {@code __label}（ENUM 补字典项名、REF 补关联实体展示名）。
 *
 * <p>业务模块只注入本接口，不依赖 {@code core.impl.*} 实现细节。底座默认实现
 * {@code com.wensaas.core.impl.customfield.resolver.CustomFieldDisplayResolverImpl}。
 *
 * <p>契约：只读 + 幂等；翻不到的 REF/ENUM 值按约定<b>不</b>加 {@code __label}（调用方按原值兜底）。
 * 调用须在租户上下文内（按租户偏好解析字典/名称）。
 */
public interface CustomFieldDisplayResolver {

    /** 翻译后追加的标签字段后缀，如 {@code cf_status__label}。 */
    String LABEL_SUFFIX = "__label";

    /**
     * 单 entity 视图填充。从 entity 类反射读 {@code @CustomFieldExtension(objectCode=...)} + extra Map；
     * 无注解时返回 {@link Optional#empty()}。
     */
    Optional<Map<String, Object>> resolveView(Object entity);

    /** 直接给 extra Map 填充（适合 REST controller 已分离 extra 时）。 */
    Map<String, Object> resolveView(String objectCode, Map<String, Object> extra);

    /**
     * 批量视图填充（同 tenant 同 objectCode）。ENUM 聚合后单次查字典、REF 聚合后单次批量翻译，
     * 避免逐行 N+1。
     */
    List<Map<String, Object>> resolveViews(String objectCode, List<? extends Map<String, Object>> extras);
}
