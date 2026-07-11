package com.wensaas.core.impl.permission.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.api.namesource.DisplayRef;
import com.wensaas.core.api.namesource.EntityTypes;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@TableName("sys_user_role")
@Getter
@Setter
public class SysUserRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    @DisplayRef(value = EntityTypes.SYS_USER, label = "成员")
    private Long userId;
    @DisplayRef(value = EntityTypes.SYS_ROLE, label = "角色")
    private Long roleId;
    private LocalDateTime createdAt;
    private Long createdBy;

}
