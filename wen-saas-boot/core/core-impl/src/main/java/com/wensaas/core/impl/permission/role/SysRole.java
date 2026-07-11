package com.wensaas.core.impl.permission.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import com.wensaas.core.impl.permission.datascope.RoleDefaultScopeStore;

/**
 * sys_role 表实体。混合表。
 *
 * <p>角色默认数据范围已退役 {@code data_scope} 列，唯一权威落点是
 * {@code sys_role_data_scope} 的 {@code action = NULL} 行（见 {@link RoleDefaultScopeStore}）。
 */
@TableName("sys_role")
@Getter
@Setter
public class SysRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private String description;
    /** 0=停用 / 1=启用 */
    private Integer status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
