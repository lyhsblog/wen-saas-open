package com.wensaas.core.impl.permission.datascope;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.api.namesource.DisplayRef;
import com.wensaas.core.api.namesource.EntityTypes;

import lombok.Getter;
import lombok.Setter;

/**
 * 角色数据范围（{@code sys_role_data_scope}）。
 *
 * <p>取代已退役的 {@code sys_role.data_scope} 单值列，按 (角色, action) 配置数据范围。
 * {@code action = NULL} 行表示角色默认范围；非 NULL 行表示对该功能点的单独覆盖。
 */
@TableName("sys_role_data_scope")
@Getter
@Setter
public class SysRoleDataScope {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    @DisplayRef(value = EntityTypes.SYS_ROLE, label = "角色")
    private Long roleId;
    /** 功能点 code（与 {@code PermissionDef.code} 对齐）；{@code null} = 该角色的默认数据范围。 */
    private String action;
    /** 数据范围 code：1=ALL / 2=DEPT_AND_BELOW / 3=SELF（见 {@link DataScope}）。 */
    private Integer dataScope;

}
