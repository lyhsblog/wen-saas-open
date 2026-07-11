package com.wensaas.core.impl.ops.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_recommended_role_template_activation 表实体（纯平台联表，模板 → 已启用租户）。
 *
 * <p>纯平台表，故 {@code tenantId} 仅作数据列、不触发租户隔离注入。
 * 租户端「启用推荐角色 → 生成快照角色」实现后写入；本期运营端只读（统计 + 删除守卫）。
 */
@TableName("sys_recommended_role_template_activation")
@Getter
@Setter
public class SysRecommendedRoleTemplateActivation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long templateId;
    private Long tenantId;
    private Long createdRoleId;
    private LocalDateTime activatedAt;
    private Long activatedBy;

}
