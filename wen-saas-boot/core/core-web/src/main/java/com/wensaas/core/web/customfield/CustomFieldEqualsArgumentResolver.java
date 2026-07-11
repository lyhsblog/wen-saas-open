package com.wensaas.core.web.customfield;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link CustomFieldEquals} 的参数解析器：摘出请求中全部 {@code cf_} 前缀、值非空白的
 * query 参数，按出现顺序放入 {@code Map<String, Object>}。多值参数取首值（等值筛选语义）。
 */
public class CustomFieldEqualsArgumentResolver implements HandlerMethodArgumentResolver {

    /** 自定义字段列统一前缀（与字段 code 生成规则、CustomFieldQueryRewriter 识别规则一致）。 */
    public static final String CF_PREFIX = "cf_";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CustomFieldEquals.class)
                && Map.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Map<String, Object> out = new LinkedHashMap<>();
        Iterator<String> names = webRequest.getParameterNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name == null || !name.startsWith(CF_PREFIX)) {
                continue;
            }
            String value = webRequest.getParameter(name);
            if (value != null && !value.isBlank()) {
                out.put(name, value);
            }
        }
        return out;
    }
}
