package com.wensaas.core.web.customfield;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 自定义字段 Web 层装配：注册 {@link CustomFieldEqualsArgumentResolver}，
 * 并让 springdoc 忽略 {@link CustomFieldEquals} 参数（cf_* 是租户级动态列，
 * 不进静态 OpenAPI 契约，接口描述里文字说明即可）。
 */
@Configuration(proxyBeanMethods = false)
public class CustomFieldWebConfiguration implements WebMvcConfigurer {

    public CustomFieldWebConfiguration() {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CustomFieldEquals.class);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CustomFieldEqualsArgumentResolver());
    }
}
