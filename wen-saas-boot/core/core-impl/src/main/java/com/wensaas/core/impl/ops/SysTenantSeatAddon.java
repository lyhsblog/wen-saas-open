package com.wensaas.core.impl.ops;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_tenant_seat_addon 表实体（平台表，不带 tenant_id 行级过滤）。
 */
@TableName("sys_tenant_seat_addon")
@Getter
@Setter
public class SysTenantSeatAddon {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private Integer quantity;
    private String remark;
    private Long operatorId;
    private Instant createdAt;

}
