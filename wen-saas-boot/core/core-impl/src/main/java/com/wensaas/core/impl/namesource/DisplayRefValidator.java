package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.DisplayRef;
import com.wensaas.core.impl.namesource.ColumnRefRegistry.RefSite;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * 启动期校验所有 {@link DisplayRef#value()}：只能是<b>已注册实体 code</b>
 * （{@code EntityNameResolver.isRegistered}），不合法即 fail-fast。
 *
 * <p>仿 {@code AuditObjectTypeValidator}，把「@DisplayRef 引用了拼错 / 未登记的 entityType」从
 * 运行期静默查空提前到启动期暴露。复用 {@link ColumnRefRegistry} 已扫好的 {@code @TableName} 实体
 * 引用站点，不二次扫描。
 *
 * <p>注意：本校验只覆盖 {@code @DisplayRef}（实体侧声明）。{@code @TransField} 响应回填
 * 的启动期校验（type 合法 / 租户端 {@code CROSS} opt-in / guarded 互斥）由
 * {@link TransFieldScanner} + {@link TransSpecRegistry} 解析期负责。
 */
public class DisplayRefValidator implements SmartInitializingSingleton {

    private final ColumnRefRegistry columnRefRegistry;
    private final EntityNameResolver resolver;

    public DisplayRefValidator(ColumnRefRegistry columnRefRegistry, EntityNameResolver resolver) {
        this.columnRefRegistry = columnRefRegistry;
        this.resolver = resolver;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (RefSite site : columnRefRegistry.refSites()) {
            if (!resolver.isRegistered(site.entityType())) {
                throw new IllegalStateException(
                        "@DisplayRef value 非法（需 EntityTypes 已注册 code）: \"" + site.entityType()
                                + "\" 于 " + site.table() + "." + site.column()
                                + "。该列引用的实体类型须有对应 NameLookup 注册。");
            }
        }
    }
}
