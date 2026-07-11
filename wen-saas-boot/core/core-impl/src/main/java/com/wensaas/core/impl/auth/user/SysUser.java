package com.wensaas.core.impl.auth.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.impl.security.EncryptedStringTypeHandler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_user 表实体。混合表，{@code tenant_id} 由 MP TenantLineInnerInterceptor 自动注入。
 *
 * <p>{@code mobile / email} 为必加密敏感字段，经
 * {@link EncryptedStringTypeHandler} 确定性 AES-256-GCM 透明加解密落库；故 {@code @TableName}
 * 须开 {@code autoResultMap}，否则 SELECT 结果映射不走 TypeHandler。
 */
@TableName(value = "sys_user", autoResultMap = true)
@Getter
@Setter
public class SysUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private String username;

    private String passwordHash;

    private String realName;

    /** 手机号。确定性 AES-256-GCM 落库加密（透明加解密）。 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String mobile;

    /** 邮箱。确定性 AES-256-GCM 落库加密（透明加解密）。 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String email;

    /** 0=未知 / 1=男 / 2=女 */
    private Integer gender;

    private LocalDate birthday;

    /** 头像文件 ID（sys_file.fileId） */
    private String avatarFileId;

    /** 员工编号（超管设定，成员只读） */
    private String empNo;

    /** 岗位（字典 POSITION 项 code 快照；单选非必填）。 */
    private String positionCode;

    /** 0=停用 / 1=正常 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private Long createdBy;

    private LocalDateTime updatedAt;

    private Long updatedBy;

    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
