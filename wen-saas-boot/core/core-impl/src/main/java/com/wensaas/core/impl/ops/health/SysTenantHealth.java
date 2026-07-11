package com.wensaas.core.impl.ops.health;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.api.ops.health.OpsHealthService.HealthMetrics;
import com.wensaas.core.api.ops.health.OpsHealthService.RiskSignal;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_tenant_health} 表实体（纯平台表，已在 platform-only-tables 白名单）。
 *
 * <p>每租户一行最新健康度快照，由 {@code TenantHealthScoreJob} 每天 upsert。
 * {@code tenantId} 是被评估租户的业务外键（非租户隔离列）。
 */
@TableName(value = "sys_tenant_health", autoResultMap = true)
@Getter
@Setter
public class SysTenantHealth {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Integer score;
    private String level;
    private String prevLevel;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private List<RiskSignal> riskSignals;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private HealthMetrics metrics;
    private Instant computedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
