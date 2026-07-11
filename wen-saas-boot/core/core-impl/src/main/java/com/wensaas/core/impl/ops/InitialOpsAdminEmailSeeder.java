package com.wensaas.core.impl.ops;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.wensaas.core.api.tenant.TenantContext;
import com.wensaas.core.impl.auth.user.SysUser;
import com.wensaas.core.impl.auth.user.SysUserMapper;
import com.wensaas.core.impl.auth.user.UserCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 初始运营超管邮箱回填（启动期，idempotent）。
 *
 * <p>sys_user.email 是确定性 AES-256-GCM 密文列（密钥按部署环境注入），迁移 SQL 无法
 * 产出密文，故种子行不带邮箱；本 Runner 在配置了
 * {@code wen-saas-boot.ops.initial-admin-email} 且账号（sys_user id=1，平台租户）
 * 邮箱仍为空时，经实体 TypeHandler 透明加密写入。已有邮箱（含人工改过）不覆盖。
 */
public class InitialOpsAdminEmailSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialOpsAdminEmailSeeder.class);

    /** 初始运营超管固定主键（见 init_data 种子）。 */
    private static final long OPS_ADMIN_ID = 1L;
    private static final IgnoreStrategy IGNORE_TENANT_LINE =
            IgnoreStrategy.builder().tenantLine(true).build();

    private final SysUserMapper userMapper;
    private final UserCommandService userCommandService;
    private final String initialAdminEmail;

    public InitialOpsAdminEmailSeeder(SysUserMapper userMapper,
                                      UserCommandService userCommandService,
                                      String initialAdminEmail) {
        this.userMapper = userMapper;
        this.userCommandService = userCommandService;
        this.initialAdminEmail = initialAdminEmail;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (initialAdminEmail == null || initialAdminEmail.isBlank()) {
            return;
        }
        InterceptorIgnoreHelper.handle(IGNORE_TENANT_LINE);
        try {
            SysUser user = userMapper.selectById(OPS_ADMIN_ID);
            if (user == null || user.getTenantId() == null
                    || user.getTenantId() != TenantContext.PLATFORM_TENANT_ID) {
                log.info("ops.admin-email-seed.skip reason=initial-admin-not-found id={}", OPS_ADMIN_ID);
                return;
            }
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                return;
            }
            SysUser patch = new SysUser();
            patch.setId(OPS_ADMIN_ID);
            patch.setEmail(initialAdminEmail.trim());
            userCommandService.updateById(patch);
            log.info("ops.admin-email-seed.done username={}", user.getUsername());
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
    }
}
