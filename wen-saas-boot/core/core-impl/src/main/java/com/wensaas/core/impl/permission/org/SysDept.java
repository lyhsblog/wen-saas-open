package com.wensaas.core.impl.permission.org;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.api.namesource.DisplayRef;
import com.wensaas.core.api.namesource.EntityTypes;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_dept 表实体。{@code ancestors} 形如 {@code /1/3/15/}，子树查询用 {@code LIKE '/3/%'}。
 */
@TableName("sys_dept")
@Getter
@Setter
public class SysDept {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    @DisplayRef(value = EntityTypes.SYS_DEPT, label = "上级部门")
    private Long parentId;
    private String name;
    private String ancestors;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
