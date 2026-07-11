package com.wensaas.core.api.compliance.encryption;

/**
 * 列表掩码规则示例（与全站通用列表掩码规则一致）。
 *
 * @param fieldType 字段类型（如「手机号」）
 * @param sample    原值示例（如「13812345678」）
 * @param masked    掩码后示例（如「138****5678」）
 */
public record MaskRuleExample(String fieldType, String sample, String masked) {
}
