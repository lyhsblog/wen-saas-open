package com.wensaas.core.impl.ops.role;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_recommended_role_template 表实体。纯平台表（无 tenant_id，已在 platform-only-tables 白名单）。
 *
 * <p>运营侧维护的推荐角色模板：草稿 / 发布 / 下架，发布后租户可一键采纳生成本租户角色。
 */
@TableName("sys_recommended_role_template")
@Getter
@Setter
public class SysRecommendedRoleTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String description;
    /** 默认数据范围（租户语义）：1=全部 / 2=本部门含下属 / 3=仅本人 */
    private Integer dataScope;
    /** 0=draft 草稿 / 1=published 已发布 / 2=inactive 已下架 */
    private Integer status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

}
