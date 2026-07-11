package com.wensaas.core.impl.permission.org;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** sys_dept_leader 表实体：部门 ↔ 负责人绑定（一个部门可设多个负责人）。 */
@TableName("sys_dept_leader")
@Getter
@Setter
public class SysDeptLeader {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long deptId;
    private Long userId;
    private LocalDateTime createdAt;
    private Long createdBy;

}
