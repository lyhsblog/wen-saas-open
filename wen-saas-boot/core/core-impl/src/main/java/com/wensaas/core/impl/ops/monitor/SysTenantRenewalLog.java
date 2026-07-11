package com.wensaas.core.impl.ops.monitor;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_tenant_renewal_log} 表实体。
 *
 * <p>运营对租户套餐的授予 / 续费 / 撤销台账，支撑平台监控看板的续费率 / 续费趋势（O9）。
 * 由 {@code TenantOpsServiceImpl} 在 assign/renew/revoke 同事务写入。{@code tenant_id} 是目标租户
 * （数据归属，进 platform-only-tables 白名单，多租户拦截器跳过）。
 */
@TableName("sys_tenant_renewal_log")
@Getter
@Setter
public class SysTenantRenewalLog {

    /** 事件类型常量。 */
    public static final String EVENT_GRANT = "grant";
    public static final String EVENT_RENEW = "renew";
    public static final String EVENT_REVOKE = "revoke";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String packageCode;
    private String eventType;
    private Instant oldExpireAt;
    private Instant newExpireAt;
    private Integer ttlDays;
    private Long operatorId;
    private LocalDateTime createdAt;

}
