package com.wensaas.core.impl.ops.setting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_setting 表实体。
 *
 * <p>纯平台表（无 tenant_id 列）；{@code value} 为 jsonb 由 {@link StringJsonbTypeHandler} 处理（保留原始 JSON 文本）。
 */
@TableName(value = "sys_setting", autoResultMap = true)
@Getter
@Setter
public class SysSetting {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String category;
    @TableField("\"key\"")
    private String key;
    @TableField(typeHandler = StringJsonbTypeHandler.class)
    private String value;
    private String description;
    private LocalDateTime updatedAt;
    private Long updatedBy;

}
