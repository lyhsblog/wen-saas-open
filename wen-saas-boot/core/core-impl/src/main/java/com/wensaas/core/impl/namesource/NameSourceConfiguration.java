package com.wensaas.core.impl.namesource;

import com.wensaas.core.api.namesource.EntityTypes;
import com.wensaas.core.api.namesource.MatchMode;
import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.namesource.NameSearch;
import com.wensaas.core.api.namesource.NameSourceFactory;
import com.wensaas.core.api.namesource.ValueTransform;
import com.wensaas.core.impl.tenant.SysTenant;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.permission.org.SysDept;
import com.wensaas.core.impl.permission.role.SysRole;
import com.wensaas.core.impl.pkg.SysPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 实体名称解析框架装配。
 *
 * <p>登记底座内建实体的 {@link NameLookup} 默认源，并暴露统一出口 {@link EntityNameResolver}。
 * 业务模块自行 {@code @Bean}/{@code @Component} 注册自己的 {@link NameLookup}，会被 resolver 自动聚合。
 */
@Configuration(proxyBeanMethods = false)
public class NameSourceConfiguration {

    /** 名称源工厂：业务模块注入后一行注册 {@link NameLookup}，无需 import {@code core.impl.*}（M10）。 */
    @Bean
    public NameSourceFactory nameSourceFactory() {
        return new LambdaNameSourceFactory();
    }

    // tenantSearchable：platform-only / 跨租户实体须为 false（sys_tenant / sys_package 在
    // platform-only-tables 白名单，租户线不注入 tenant_id，租户端搜会跨租户泄露）。
    // displayLabel 与 @Auditable.objectType 惯例对齐（成员 / 部门 / 角色），使审计「对象类型」筛选可命中。

    // 全部 render 取默认 PRIMARY（展示仅主字段，与改造前 DisplayFormat.NAME 一致）；role/package 的 code
    // 作 secondary，只参与搜索（EXACT），不进展示串。无 code 实体只声明 primary。

    @Bean
    public NameLookup tenantNameSource(NameSourceFactory factory) {
        return factory.source(EntityTypes.SYS_TENANT, "租户", false, SysTenant.class, SysTenant::getId)
                .primary(SysTenant::getName, "名称", MatchMode.PREFIX)
                .build();
    }

    @Bean
    public NameLookup userNameSource(NameSourceFactory factory) {
        return factory.source(EntityTypes.SYS_USER, "成员", true, SysUser.class, SysUser::getId)
                .primary(SysUser::getRealName, "姓名", MatchMode.PREFIX)
                .build();
    }

    @Bean
    public NameLookup deptNameSource(NameSourceFactory factory) {
        return factory.source(EntityTypes.SYS_DEPT, "部门", true, SysDept.class, SysDept::getId)
                .primary(SysDept::getName, "名称", MatchMode.PREFIX)
                .build();
    }

    @Bean
    public NameLookup roleNameSource(NameSourceFactory factory) {
        // referenceable=false：角色是权限构件、非数据记录，可被审计「对象」筛选搜到，但不作自定义字段 REF 等
        // 关联引用目标（其余实体默认 referenceable=true）。
        return factory.source(EntityTypes.SYS_ROLE, "角色", true, SysRole.class, SysRole::getId)
                .primary(SysRole::getName, "名称", MatchMode.PREFIX)
                .secondary(SysRole::getCode, "编码", MatchMode.EXACT)
                .referenceable(false)
                .build();
    }

    @Bean
    public NameLookup packageNameSource(NameSourceFactory factory) {
        return factory.source(EntityTypes.SYS_PACKAGE, "套餐", false, SysPackage.class, SysPackage::getId)
                .primary(SysPackage::getName, "名称", MatchMode.PREFIX)
                .secondary(SysPackage::getCode, "编码", MatchMode.EXACT)
                .build();
    }

    @Bean
    public EntityNameResolver entityNameResolver(List<NameLookup> lookups) {
        return new EntityNameResolver(lookups);
    }

    @Bean
    public EntitySearchService entitySearchService(List<NameSearch> searches, List<NameLookup> lookups) {
        return new EntitySearchService(searches, lookups);
    }

    // ===================== 响应关联名回填框架 =====================

    @Bean
    public TransSpecRegistry transSpecRegistry() {
        return new TransSpecRegistry();
    }

    /** 自定义逻辑转换注册表：业务模块 {@code @Bean}/{@code @Component} 注册 {@code ValueTransform} 即被聚合。 */
    @Bean
    public ValueTransformRegistry valueTransformRegistry(List<ValueTransform> transforms,
                                                         EntityNameResolver resolver) {
        return new ValueTransformRegistry(transforms, resolver);
    }

    @Bean
    public TransFiller transFiller(EntityNameResolver resolver, TransSpecRegistry transSpecRegistry,
                                   ValueTransformRegistry valueTransformRegistry) {
        return new TransFiller(resolver, transSpecRegistry, valueTransformRegistry);
    }

    @Bean
    public TransFieldScanner transFieldScanner(TransSpecRegistry transSpecRegistry,
                                               EntityNameResolver resolver) {
        return new TransFieldScanner(transSpecRegistry, resolver);
    }

    @Bean
    public DisplayRefValidator displayRefValidator(ColumnRefRegistry columnRefRegistry,
                                                   EntityNameResolver resolver) {
        return new DisplayRefValidator(columnRefRegistry, resolver);
    }
}
