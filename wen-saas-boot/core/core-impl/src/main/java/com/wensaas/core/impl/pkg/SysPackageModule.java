package com.wensaas.core.impl.pkg;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.api.namesource.DisplayRef;
import com.wensaas.core.api.namesource.EntityTypes;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_package_module 表实体（纯平台联表，套餐 → 业务模块 N:N）。
 */
@TableName("sys_package_module")
@Getter
@Setter
public class SysPackageModule {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @DisplayRef(value = EntityTypes.SYS_PACKAGE, label = "套餐")
    private Long packageId;
    private String moduleCode;
    private LocalDateTime createdAt;

}
