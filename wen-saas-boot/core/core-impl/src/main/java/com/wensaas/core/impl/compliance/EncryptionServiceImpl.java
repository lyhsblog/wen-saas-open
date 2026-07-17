package com.wensaas.core.impl.compliance;

import com.wensaas.core.api.audit.Sensitives;
import com.wensaas.core.api.compliance.encryption.EncryptionOverview;
import com.wensaas.core.api.compliance.encryption.EncryptionService;
import com.wensaas.core.api.compliance.encryption.MaskRuleExample;
import com.wensaas.core.api.compliance.encryption.SensitiveFieldRow;
import com.wensaas.core.impl.security.SensitiveFieldCipher;

import java.util.List;

/**
 * 敏感字段加密自助透明面实现。
 *
 * <p>字段登记表为声明式常量，即「必加密字段」的权威清单；底座状态（算法 / 密钥就绪 /
 * activeKeyId）取自 {@link SensitiveFieldCipher}；掩码示例由 {@link Sensitives} 实时计算，
 * 与审计日志、列表掩码同一套规则。
 */
public class EncryptionServiceImpl implements EncryptionService {

    static final String ALGORITHM = "AES-256-GCM";

    /** 加密底座说明文案。 */
    private static final String DETERMINISTIC = "确定性 AES-256-GCM（支持唯一性等值校验）";
    private static final String CREDENTIAL = "CredentialCipher（随机 IV）";

    /** 敏感字段登记表（声明式，全租户一致）。 */
    private static final List<SensitiveFieldRow> FIELDS = List.of(
            new SensitiveFieldRow("主管理员手机号", "登录与账号 / 成员管理",
                    true, DETERMINISTIC, "PHONE", Sensitives.maskPhone("13812345678")),
            new SensitiveFieldRow("主管理员邮箱", "登录与账号 / 成员管理",
                    true, DETERMINISTIC, "EMAIL", Sensitives.maskEmail("admin@example.com")),
            new SensitiveFieldRow("成员手机号", "成员管理",
                    true, DETERMINISTIC, "PHONE", Sensitives.maskPhone("13912345678")),
            new SensitiveFieldRow("成员邮箱", "成员管理",
                    true, DETERMINISTIC, "EMAIL", Sensitives.maskEmail("alice@example.com")),
            new SensitiveFieldRow("API Secret", "集成与开放",
                    true, CREDENTIAL, "SECRET", "ak_live_****"),
            new SensitiveFieldRow("Webhook 签名密钥", "集成与开放",
                    true, CREDENTIAL, "SECRET", "whsec_****"),
            new SensitiveFieldRow("租户 OSS 凭证", "文件中心",
                    true, CREDENTIAL, "SECRET", "LTAI****"),
            new SensitiveFieldRow("平台 OSS 凭证", "平台存储",
                    true, CREDENTIAL, "SECRET", "LTAI****"),
            new SensitiveFieldRow("MFA / TOTP 密钥", "登录与账号",
                    true, CREDENTIAL, "SECRET", "****"));

    /** 列表掩码规则示例。 */
    private static final List<MaskRuleExample> MASK_RULES = List.of(
            new MaskRuleExample("手机号", "13812345678", Sensitives.maskPhone("13812345678")),
            new MaskRuleExample("邮箱", "alice@example.com", Sensitives.maskEmail("alice@example.com")),
            new MaskRuleExample("身份证", "310115199001010011", Sensitives.maskIdCard("310115199001010011")),
            new MaskRuleExample("银行卡", "6222021234567890123", Sensitives.maskBankCard("6222021234567890123")));

    private final SensitiveFieldCipher cipher;

    public EncryptionServiceImpl(SensitiveFieldCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public EncryptionOverview overview() {
        // 加密底座状态为全平台一致的事实，不依赖租户上下文（运营端平台自查调用）。
        return new EncryptionOverview(
                ALGORITHM, cipher.keyConfigured(), cipher.activeKeyId(), FIELDS, MASK_RULES);
    }
}
