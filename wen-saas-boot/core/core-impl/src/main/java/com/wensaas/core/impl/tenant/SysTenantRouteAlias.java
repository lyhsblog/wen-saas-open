package com.wensaas.core.impl.tenant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_tenant_route_alias 实体（纯平台表，无 tenant_id 字段）。
 *
 * <p>租户改路由位（code）后写入一条别名记录：旧 code 在 {@code expiresAt} 前仍可被公开查询解析、
 * 自动重定向到租户当前 code（旧链接 / 收藏不即刻失效）；宽限期过后别名作废，旧 code 可释放复用。
 */
@TableName("sys_tenant_route_alias")
@Getter
@Setter
public class SysTenantRouteAlias {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String oldCode;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

}
