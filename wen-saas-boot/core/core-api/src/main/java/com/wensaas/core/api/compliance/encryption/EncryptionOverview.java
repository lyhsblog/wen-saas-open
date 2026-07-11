package com.wensaas.core.api.compliance.encryption;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 敏感字段加密总览。
 *
 * <p>面向主管理员的自助透明面：声明底座算法 / 密钥就绪状态 + 敏感字段登记表（哪些字段落库加密、
 * 掩码规则）。手机号 / 邮箱由确定性 AES-256-GCM 透明加解密落库；API Secret / OSS 凭证等由
 * CredentialCipher（随机 IV）加密。掩码规则与全站通用列表掩码规则一致。
 *
 * @param algorithm     落库加密算法（AES-256-GCM）
 * @param keyConfigured 加密密钥是否已就绪（未就绪则手机/邮箱退化为明文存储）
 * @param activeKeyId   当前生效密钥版本 id（多版本密钥轮转用）
 * @param fields        敏感字段登记表（声明式）
 * @param maskRules     列表掩码规则示例
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class EncryptionOverview {
    private String algorithm;
    private boolean keyConfigured;
    private String activeKeyId;
    private List<SensitiveFieldRow> fields;
    private List<MaskRuleExample> maskRules;

}
