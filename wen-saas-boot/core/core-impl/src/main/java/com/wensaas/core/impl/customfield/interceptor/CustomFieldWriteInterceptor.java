package com.wensaas.core.impl.customfield.interceptor;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.wensaas.core.api.customfield.CustomFieldDef;
import com.wensaas.core.api.tenant.TenantContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.wensaas.core.impl.customfield.def.CustomFieldValueValidator;
import com.wensaas.core.impl.customfield.resolver.CustomFieldResolver;

/**
 * MP {@link InnerInterceptor} v2 写入校验：拦 INSERT / UPDATE，若 entity 带
 * {@code @CustomFieldExtension} 注解则自动调 {@link CustomFieldValueValidator#validate} 校验
 * {@code extra} 容器中的值；业务方不再需要主动调
 * {@code customFieldService.validateValues(...)}。
 *
 * <p>parameter 形态兼容：
 * <ul>
 *   <li>单 entity（{@code mapper.insert(e)}）</li>
 *   <li>{@code Collection<Entity>}（自定义批量 XML）</li>
 *   <li>{@code Map} 形态（MP {@code update(entity, wrapper)} → key {@code et}；
 *   {@code @Param("list")} 批量 → key {@code list / collection / array}）</li>
 * </ul>
 *
 * <p>校验失败抛 {@link com.wensaas.core.api.customfield.CustomFieldException}（业务异常被
 * REST 兜底 advice 转 400）。
 */
public class CustomFieldWriteInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CustomFieldWriteInterceptor.class);

    /** MP / MyBatis 约定的 entity 别名 key。 */
    private static final Set<String> ENTITY_PARAM_KEYS = Set.of("et", "entity", "list", "collection", "array");

    private final java.util.function.Supplier<CustomFieldResolver> resolverSupplier;
    private final java.util.function.Supplier<CustomFieldValueValidator> validatorSupplier;

    /**
     * 生产路径构造：走 ObjectProvider 延迟解析，避开 mybatisPlusInterceptor →
     * resolver → mapper → sqlSessionFactory → mybatisPlusInterceptor 循环依赖。
     */
    public CustomFieldWriteInterceptor(ObjectProvider<CustomFieldResolver> resolverProvider,
                                         ObjectProvider<CustomFieldValueValidator> validatorProvider) {
        this.resolverSupplier = resolverProvider::getObject;
        this.validatorSupplier = validatorProvider::getObject;
    }

    /** 单元测试便捷构造（直接传实例）。 */
    CustomFieldWriteInterceptor(CustomFieldResolver resolver,
                                  CustomFieldValueValidator validator) {
        this.resolverSupplier = () -> resolver;
        this.validatorSupplier = () -> validator;
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) {
        SqlCommandType type = ms.getSqlCommandType();
        if (type != SqlCommandType.INSERT && type != SqlCommandType.UPDATE) {
            return;
        }
        if (InterceptorIgnoreHelper.willIgnoreTenantLine(ms.getId())) {
            // 业务方主动 @CrossTenantQuery 标记，校验同步跳过（无明确 tenant 上下文）
            return;
        }
        for (Object entity : collectEntities(parameter)) {
            validateOne(entity);
        }
    }

    private void validateOne(Object entity) {
        Optional<CustomFieldEntitySupport.EntityMeta> metaOpt =
                CustomFieldEntitySupport.metaOf(entity.getClass());
        if (metaOpt.isEmpty()) {
            return;
        }
        CustomFieldEntitySupport.EntityMeta meta = metaOpt.get();
        Map<String, Object> extra = CustomFieldEntitySupport.readExtra(entity, meta);
        if (extra == null || extra.isEmpty()) {
            return; // 未提供任何自定义字段值；required 校验由 validator 在 defs 维度判定
        }
        long tenantId = TenantContext.require();
        List<CustomFieldDef> defs = resolverSupplier.get().resolveDefs(tenantId, meta.objectCode());
        if (defs.isEmpty()) {
            log.debug("[CustomField] entity {} 未配置任何字段，跳过校验",
                    entity.getClass().getSimpleName());
            return;
        }
        validatorSupplier.get().validate(defs, extra);
    }

    /**
     * 把 parameter 展开为 entity 列表。
     *
     * <p>仅展开 {@link Collection} 与 {@link Map} 中 entity-like key；其它对象作为单 entity 处理。
     * 不递归展开任意嵌套 Map 以避免误触 Wrapper / 内部容器。
     */
    private static List<Object> collectEntities(Object parameter) {
        if (parameter == null) {
            return List.of();
        }
        if (parameter instanceof Collection<?> c) {
            return new ArrayList<>(c);
        }
        if (parameter instanceof Map<?, ?> map) {
            List<Object> out = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                if (key instanceof String s && ENTITY_PARAM_KEYS.contains(s)) {
                    if (value instanceof Collection<?> coll) {
                        out.addAll(coll);
                    } else {
                        out.add(value);
                    }
                }
            }
            return out;
        }
        return List.of(parameter);
    }
}
