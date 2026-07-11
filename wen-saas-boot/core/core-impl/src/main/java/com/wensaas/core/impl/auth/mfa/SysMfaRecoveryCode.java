package com.wensaas.core.impl.auth.mfa;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_mfa_recovery_code 表实体（平台表）。
 */
@TableName("sys_mfa_recovery_code")
@Getter
@Setter
public class SysMfaRecoveryCode {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private Long userId;
    /** 恢复码 SHA-256 hex。 */
    private String codeHash;
    private Instant usedAt;
    private Instant createdAt;

}
