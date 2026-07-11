package com.wensaas.core.api.compliance.encryption;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 敏感字段登记表一行。声明式，全租户一致。
 *
 * @param field           字段名（如「成员手机号」）
 * @param sourceModule    来源模块（如「成员管理」）
 * @param encryptedAtRest 是否落库加密（AES-256-GCM）
 * @param cipher          加密底座说明（如「确定性 AES-256-GCM」/「CredentialCipher（随机 IV）」）；未加密为 null
 * @param maskType        列表掩码类型（PHONE / EMAIL / ID_CARD / SECRET 等）；无掩码为 null
 * @param maskExample     列表掩码示例（如「138****5678」）；无掩码为 null
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class SensitiveFieldRow {
    private String field;
    private String sourceModule;
    private boolean encryptedAtRest;
    private String cipher;
    private String maskType;
    private String maskExample;

}
