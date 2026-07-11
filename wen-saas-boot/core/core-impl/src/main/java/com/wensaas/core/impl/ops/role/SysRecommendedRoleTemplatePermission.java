package com.wensaas.core.impl.ops.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_recommended_role_template_permission 表实体（纯平台联表，模板 → 包含权限点 N:N）。
 */
@TableName("sys_recommended_role_template_permission")
@Getter
@Setter
public class SysRecommendedRoleTemplatePermission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long templateId;
    private String permissionCode;
    private LocalDateTime createdAt;

}
