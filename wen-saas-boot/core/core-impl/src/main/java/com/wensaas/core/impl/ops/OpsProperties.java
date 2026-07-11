package com.wensaas.core.impl.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 运营域顶层配置。
 *
 * <pre>
 * wen-saas-boot:
 *   ops:
 *     initial-admin-email: ops-admin@example.com
 * </pre>
 */
@ConfigurationProperties(prefix = "wen-saas-boot.ops")
@Getter
@Setter
public class OpsProperties {

    /**
     * 初始运营超管（sys_user id=1）的邮箱。sys_user.email 是确定性 AES 密文列，
     * 迁移 SQL 无法直接写入，由 {@link InitialOpsAdminEmailSeeder} 启动时加密回填：
     * 仅当该账号邮箱为空时写入（不覆盖后续人工修改），留空则跳过。
     */
    private String initialAdminEmail;

}
