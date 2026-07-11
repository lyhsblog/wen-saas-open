package com.wensaas.core.api.notification.template;

import java.util.Objects;

/**
 * 模板变量声明。业务方通过 {@code NotificationTemplateDef.availableVars} 声明，
 * 用于主管理员预览（sampleValue）与未来变量校验。
 *
 * @param name        变量名（不含大括号；用 ASCII 字母/数字/下划线）
 * @param sampleValue 示例值（用于预览）
 * @param required    是否必填（v1 仅记录，未做校验拦截）
 */
public record TemplateVariableDef(String name, String sampleValue, boolean required) {

    public TemplateVariableDef {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("变量名不可空");
        }
    }

    public static TemplateVariableDef of(String name, String sampleValue) {
        return new TemplateVariableDef(name, sampleValue, false);
    }
}
