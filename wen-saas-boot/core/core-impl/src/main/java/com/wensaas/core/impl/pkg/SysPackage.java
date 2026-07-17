package com.wensaas.core.impl.pkg;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_package 表实体。纯平台表（无 tenant_id，已在 wen-saas-boot.tenant.platform-only-tables 白名单）。
 *
 * <p>v1 数据模型 + capabilities 读路径；v2 (2026-05-28) 加 admin CRUD + 4 态状态机。
 */
@TableName("sys_package")
@Getter
@Setter
public class SysPackage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private String name;
    private String description;
    private Boolean isSystem;
    /** 0=draft / 1=active / 2=archived / 3=inactive */
    private Integer status;
    /** 默认有效期（天）；NULL 表示永久 */
    private Integer defaultTtlDays;
    /** 来源业务模块编码（SPI 声明的预置套餐）；NULL=运营手工创建。非 NULL 不可物理删除 */
    private String sourceModule;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;

}
