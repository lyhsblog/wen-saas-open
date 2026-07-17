package com.wensaas.core.impl.security;

import com.wensaas.core.impl.auth.AuthProperties;
import com.wensaas.core.impl.auth.mfa.MfaProperties;
import com.wensaas.core.impl.file.FileProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产环境弱密钥兜底。
 *
 * <p>JWT / MFA / 文件 AES 等密钥在 {@code application.yml} 都带「dev-only」内置默认值，便于本地起步；
 * 这些默认值是<b>写在源码 / git 里的公开串</b>，且各自只校验长度合法、不校验是否为已知弱值。一旦
 * 生产环境漏配对应环境变量，应用会<b>正常启动并用公开弱密钥</b>签发 JWT / 加密 TOTP / 加密凭据——
 * 等于把签名与加密能力公开。
 *
 * <p>本守卫在<b>启动期</b>（{@link InitializingBean#afterPropertiesSet()}）检查：当 active profile
 * 含 {@code prod} 时，若任一密钥仍等于内置 dev 默认值，则<b>直接抛异常阻断启动</b>（fail-fast），
 * 把「漏配 → 静默用公开密钥」变成「漏配 → 起不来」。非 prod profile 不校验。
 */
public class ProdSecretGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProdSecretGuard.class);

    /** 与各 *Properties 内置默认值保持一致；改动默认值时同步这里。 */
    static final String DEV_JWT_SECRET = "dev-only-secret-please-change-in-prod-32bytes!";
    static final String DEV_MFA_KEY = "dev-only-mfa-secret-change-in-prod";
    static final String DEV_MFA_SALT = "5c0744940b5c369b";
    static final String DEV_FILE_AES_KEY = "ZGV2LW9ubHktYWVzLWtleS1ub3QtZm9yLXByb2QhISE=";

    private final Environment environment;
    private final AuthProperties authProperties;
    private final MfaProperties mfaProperties;
    private final FileProperties fileProperties;

    public ProdSecretGuard(Environment environment,
                           AuthProperties authProperties,
                           MfaProperties mfaProperties,
                           FileProperties fileProperties) {
        this.environment = environment;
        this.authProperties = authProperties;
        this.mfaProperties = mfaProperties;
        this.fileProperties = fileProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!isProd()) {
            return;
        }
        // M3 纵深加固：生产环境开 allow-tenant-id-header 等于让任意客户端经 X-Tenant-Id 自指定租户上下文，
        // 仅应在 dev / 内部探针启用。这里只 WARN 不阻断（可能是部署方有意为之的内网探针场景）。
        if (authProperties.isAllowTenantIdHeader()) {
            log.warn("⚠ 生产环境(prod) 开启了 wen-saas-boot.auth.allow-tenant-id-header=true："
                    + "任意客户端可经 X-Tenant-Id header 指定租户上下文，存在越权风险，"
                    + "请确认仅用于内网探针等可信场景。");
        }

        List<String> violations = new ArrayList<>();
        check(violations, "JWT 签名密钥", "WEN_SAAS_BOOT_JWT_SECRET",
                authProperties.getJwtSecret(), DEV_JWT_SECRET);
        check(violations, "MFA 加密口令", "WEN_SAAS_BOOT_MFA_KEY",
                mfaProperties.getSecretKey(), DEV_MFA_KEY);
        check(violations, "MFA 加密盐", "WEN_SAAS_BOOT_MFA_SALT",
                mfaProperties.getSecretSalt(), DEV_MFA_SALT);
        check(violations, "文件 / 敏感字段 AES 密钥", "WEN_SAAS_BOOT_FILE_AES_KEY",
                fileProperties.resolveKeys().get("default"), DEV_FILE_AES_KEY);

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "检测到生产环境(prod)仍在使用内置的 dev 弱密钥，已阻断启动。请注入对应环境变量后重启：\n  - "
                            + String.join("\n  - ", violations));
        }
        log.info("prod-secret-guard: 生产密钥校验通过（JWT / MFA / 文件 AES 均非内置 dev 默认值）");
    }

    private boolean isProd() {
        for (String p : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    private static void check(List<String> violations, String name, String envVar,
                              String actual, String devDefault) {
        if (devDefault.equals(actual)) {
            violations.add(name + " 仍为内置 dev 默认值 → 请设置环境变量 " + envVar);
        }
    }
}
