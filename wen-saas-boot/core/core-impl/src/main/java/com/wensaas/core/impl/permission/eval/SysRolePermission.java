package com.wensaas.core.impl.permission.eval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.api.namesource.DisplayRef;
import com.wensaas.core.api.namesource.EntityTypes;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@TableName("sys_role_permission")
@Getter
@Setter
public class SysRolePermission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    @DisplayRef(value = EntityTypes.SYS_ROLE, label = "角色")
    private Long roleId;
    private String permissionCode;
    private LocalDateTime createdAt;
    private Long createdBy;

}
