package com.wensaas.core.impl.permission.org;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.api.namesource.DisplayRef;
import com.wensaas.core.api.namesource.EntityTypes;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@TableName("sys_user_dept")
@Getter
@Setter
public class SysUserDept {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    @DisplayRef(value = EntityTypes.SYS_USER, label = "成员")
    private Long userId;
    @DisplayRef(value = EntityTypes.SYS_DEPT, label = "部门")
    private Long deptId;
    /** 1=主部门（每用户至多 1 条） / 0=兼任 */
    private Integer isMain;
    private LocalDateTime createdAt;
    private Long createdBy;

}
