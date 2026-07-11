package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.CrossTenantView;
import com.wensaas.core.impl.namesource.TransSpecRegistry.ClassSpec;
import com.wensaas.core.impl.namesource.TransSpecRegistry.TransSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * 启动期扫描所有 {@link CrossTenantView}（运营专属）DTO，force 解析 + 校验其 {@code @TransField}。
 *
 * <p><b>为什么扫的是 {@code @CrossTenantView} 而非全部 DTO</b>：危险的只有跨租户 {@code CROSS} 字段，而
 * {@code CROSS} 经 {@link TransSpecRegistry} 解析期强制「必须标 {@code @CrossTenantView}」——所以遍历
 * 全部 marker 类即覆盖了全部 {@code CROSS} 使用点（用类级 marker 做 {@link AnnotationTypeFilter} 入口，
 * ASM 只读元数据、仅加载命中的少数 marker 类，启动成本极低）。
 *
 * <p>对每个 marker 类：① {@code specFor} 触发解析期校验（ref 存在 / {@code CROSS}⟹marker 一致 /
 * 与 {@code @RelatedObjectGuarded} 互斥）；② 额外校验每个 {@code @TransField.type} 已注册对应
 * {@code NameLookup}，拼错即 fail-fast（仿 {@link DisplayRefValidator}）。
 *
 * <p>「租户端 DTO 误标 {@code CROSS} 却没标 {@code @CrossTenantView}」这类<b>未 opt-in</b>的误用，由
 * {@code TransSpecRegistry} 解析期在该 DTO 被 {@code fill} 触达时 fail-fast 兜底（泄露前即拦，无盲区）；
 * 本扫描器把<b>已 opt-in</b>视图的一致性问题进一步提前到启动期。
 */
public class TransFieldScanner implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(TransFieldScanner.class);
    private static final String BASE_PACKAGE = "com.wensaas";

    private final TransSpecRegistry registry;
    private final EntityNameResolver resolver;

    public TransFieldScanner(TransSpecRegistry registry, EntityNameResolver resolver) {
        this.registry = registry;
        this.resolver = resolver;
    }

    @Override
    public void afterSingletonsInstantiated() {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(CrossTenantView.class));
        int scanned = 0;
        for (BeanDefinition bd : provider.findCandidateComponents(BASE_PACKAGE)) {
            String className = bd.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> clazz;
            try {
                clazz = ClassUtils.forName(className, getClass().getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("扫描 @CrossTenantView DTO 时加载类失败: " + className, e);
            }
            ClassSpec spec = registry.specFor(clazz); // 触发解析期校验
            for (TransSpec ts : spec.transSpecs()) {
                if (!resolver.isRegistered(ts.type())) {
                    throw new IllegalStateException("@TransField.type 非法（需已注册 NameLookup code）: \""
                            + ts.type() + "\" 于 " + clazz.getName() + "#" + ts.targetField().getName());
                }
            }
            scanned++;
        }
        log.info("transfield.scanner.done scannedCrossTenantViewDtos={}", scanned);
    }
}
