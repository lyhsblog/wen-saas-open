package com.wensaas.core.impl.auth.password;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_password_reset_token 表实体（平台表，不带 tenant_id 行级过滤）。
 */
@TableName("sys_password_reset_token")
@Getter
@Setter
public class SysPasswordResetToken {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String token;
    private Long userId;
    private Long tenantId;
    private String tenantCode;
    private String usernameMask;
    private String purpose;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant invalidatedAt;
    private Instant createdAt;
    private Long createdBy;

}
