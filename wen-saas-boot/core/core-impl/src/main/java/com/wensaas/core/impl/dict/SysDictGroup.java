package com.wensaas.core.impl.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;
import com.wensaas.core.api.spi.def.DictExtraFieldDef;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_dict_group 表实体。混合表。
 *
 * <p>{@code autoResultMap=true} 让 MP 自动为带 typeHandler 的字段生成 resultMap，
 * {@code extraSchema} 的 jsonb 列由 {@link JacksonTypeHandler} 序列化。
 */
@TableName(value = "sys_dict_group", autoResultMap = true)
@Getter
@Setter
public class SysDictGroup {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private String description;
    /** 1=SYSTEM / 2=MODULE / 3=CUSTOM */
    private Integer source;
    private String moduleCode;
    private Boolean supportHierarchy;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private List<DictExtraFieldDef> extraSchema;
    /** 0=停用 / 1=启用 */
    private Integer status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
