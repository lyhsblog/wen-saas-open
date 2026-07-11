package com.wensaas.crmdemo.namesource;

import com.wensaas.core.api.namesource.MatchMode;
import com.wensaas.core.api.namesource.NameLookup;
import com.wensaas.core.api.namesource.NameSourceFactory;
import com.wensaas.crmdemo.entity.CrmDemoCustomer;
import com.wensaas.crmdemo.spi.CrmDemoModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 客户「id → 展示名」翻译源，一份 Bean 同时服务：
 * <ul>
 *   <li>类型 label「客户」——字段管理 UI 的宿主对象选择项（{@link NameLookup#displayLabel()}）</li>
 *   <li>实例名翻译——自定义字段 REF（{@code cf_parent} 集团母公司自关联）的 {@code __label} 填充</li>
 * </ul>
 *
 * <p>经底座 {@link NameSourceFactory} 一行注册，纯 api 依赖、不下沉 {@code core.impl.*}（架构审查 M10）。
 * entityType 必须与 {@code @CustomFieldExtension.objectCode} / {@code CustomFieldObjectDef.code}
 * 一致（{@code crm_demo.customer}）。REF 翻不到的 id（已删除）按契约从结果 Map 略去，前端按 id 兜底。
 */
@Configuration(proxyBeanMethods = false)
public class CrmDemoNameSourceConfiguration {

    @Bean
    public NameLookup crmDemoCustomerNameSource(NameSourceFactory nameSourceFactory) {
        return nameSourceFactory
                .source(CrmDemoModule.OBJECT_CUSTOMER, "客户", true, CrmDemoCustomer.class,
                        CrmDemoCustomer::getId)
                .primary(CrmDemoCustomer::getName, "名称", MatchMode.PREFIX)
                .secondary(CrmDemoCustomer::getCode, "编码", MatchMode.EXACT)
                .build();
    }
}
