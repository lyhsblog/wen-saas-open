package com.wensaas.core.impl.spi.module;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_business_module 表实体。纯平台表（无 tenant_id，已在 wen-saas-boot.tenant.platform-only-tables 白名单）。
 *
 * <p>由 {@link BusinessModuleDbSynchronizer} 在 SPI 启动期注册阶段维护。
 */
@TableName("sys_business_module")
@Getter
@Setter
public class SysBusinessModule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String code;

    private String name;

    private String version;

    private String description;

    private String icon;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
