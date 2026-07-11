package com.wensaas.core.impl.auth.mfa;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_mfa_config 表实体（平台表，不带 tenant_id 行级过滤）。每用户一行：
 * 身份验证器绑定（totpSecret 非空=已绑定，独立存续）+ 登录二步验证开关。
 */
@TableName("sys_mfa_config")
@Getter
@Setter
public class SysMfaConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private Long userId;
    /** TOTP 密钥密文（AES）；非空=已绑定身份验证器。 */
    private String totpSecret;
    /** 登录二步验证开关；开启后登录通道服务端推导（已绑 TOTP→TOTP，否则邮箱）。 */
    private Boolean loginMfaEnabled;
    /** 登录二步验证开启时间；null=未开启。 */
    private Instant enabledAt;
    private Instant createdAt;
    private Instant updatedAt;

}
