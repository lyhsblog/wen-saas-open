package com.wensaas.core.api.compliance.encryption;

/**
 * 敏感字段加密底座自查。只读、全平台一致（不依赖租户上下文），由运营端平台
 * 自查呈现（算法 / 密钥版本属平台运维信息，不暴露给租户）。
 */
public interface EncryptionService {

    /** 敏感字段加密总览（底座状态 + 字段登记表 + 掩码规则）。 */
    EncryptionOverview overview();
}
