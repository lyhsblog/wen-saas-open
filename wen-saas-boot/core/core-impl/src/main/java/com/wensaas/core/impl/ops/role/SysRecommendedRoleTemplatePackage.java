package com.wensaas.core.impl.ops.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_recommended_role_template_package 表实体（纯平台联表，模板 → 关联套餐包 N:N）。
 */
@TableName("sys_recommended_role_template_package")
@Getter
@Setter
public class SysRecommendedRoleTemplatePackage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long templateId;
    private String packageCode;
    private LocalDateTime createdAt;

}
