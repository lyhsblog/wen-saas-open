package com.wensaas.core.impl.tenant;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.wensaas.core.impl.audit.aspect.AuditContextGucInterceptor;
import com.wensaas.core.impl.customfield.interceptor.CustomFieldQueryRewriter;
import com.wensaas.core.impl.customfield.interceptor.CustomFieldWriteInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.wensaas.core.impl.tenant.isolation.TenantLineHandlerImpl;

/**
 * MyBatis-Plus 拦截器装配。
 *
 * <p>InnerInterceptor 顺序：
 * <ol>
 *   <li>{@link AuditContextGucInterceptor}：写事务前注入 requestId/actor GUC，供 ③ 数据变更触发器读取（只 set_config 不改 SQL，顺序无关）</li>
 *   <li>{@link TenantLineInnerInterceptor}：先按 tenant_id 隔离</li>
 *   <li>{@link DataPermissionInterceptor}：再按数据范围过滤（可选）</li>
 *   <li>{@link CustomFieldQueryRewriter}：v2 SELECT 翻译 {@code cf_*} 列为 jsonb 操作符（可选）</li>
 *   <li>{@link CustomFieldWriteInterceptor}：v2 INSERT/UPDATE 自动校验 {@code @CustomFieldExtension} entity（可选）</li>
 *   <li>{@link PaginationInnerInterceptor}：最后做分页（LIMIT + COUNT）；放在过滤类拦截器之后，
 *       其 COUNT 子查询会重入拦截链，因此仍带上 tenant_id / 数据权限条件</li>
 *   <li>{@link BlockAttackInnerInterceptor}：链尾兜底，拦截全表 UPDATE/DELETE（无 WHERE）。置于
 *       TenantLine / 数据权限之后——租户表 / 数据范围写此时已被追加 WHERE 条件、不会误伤；只有
 *       纯平台表（无租户线）真正无条件的 update/delete 才会被挡，防误写全表生效</li>
 * </ol>
 *
 * <p>所有可选 handler 走 {@link ObjectProvider#ifAvailable}：对应功能模块关闭时不参与装配，不阻塞启动。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantProperties.class)
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantLineHandlerImpl tenantLineHandler,
                                                          ObjectProvider<DataPermissionHandler> dataPermissionHandler,
                                                          ObjectProvider<CustomFieldQueryRewriter> customFieldQueryRewriter,
                                                          ObjectProvider<CustomFieldWriteInterceptor> customFieldWriteInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // ③ 审计上下文 GUC 注入：写事务前 set_config requestId/actor，供数据变更触发器读取
        interceptor.addInnerInterceptor(new AuditContextGucInterceptor());
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        dataPermissionHandler.ifAvailable(h -> interceptor.addInnerInterceptor(new DataPermissionInterceptor(h)));
        customFieldQueryRewriter.ifAvailable(interceptor::addInnerInterceptor);
        customFieldWriteInterceptor.ifAvailable(interceptor::addInnerInterceptor);
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        // 链尾兜底：拦截无 WHERE 的全表 update/delete（置于 TenantLine 之后，租户/数据范围写已带条件不误伤）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
