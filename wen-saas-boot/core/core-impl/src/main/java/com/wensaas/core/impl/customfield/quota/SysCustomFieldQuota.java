package com.wensaas.core.impl.customfield.quota;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_custom_field_quota 表实体。混合表。
 *
 * <p>PK = tenant_id（非自增），故 {@link IdType#INPUT}。
 */
@TableName("sys_custom_field_quota")
@Getter
@Setter
public class SysCustomFieldQuota {

    @TableId(value = "tenant_id", type = IdType.INPUT)
    private Long tenantId;
    private Integer fieldQuota;
    private Integer fieldUsed;
    private Integer indexQuota;
    private Integer indexUsed;
    private LocalDateTime updatedAt;

}
