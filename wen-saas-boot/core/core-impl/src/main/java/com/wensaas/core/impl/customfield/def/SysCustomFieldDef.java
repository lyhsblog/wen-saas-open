package com.wensaas.core.impl.customfield.def;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_custom_field_def 表实体。混合表。
 */
@TableName("sys_custom_field_def")
@Getter
@Setter
public class SysCustomFieldDef {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String objectCode;
    private String fieldCode;
    private String fieldName;
    /** text / long_text / number / date / bool / enum / ref */
    private String fieldType;
    private Boolean isRequired;
    private Boolean isIndexed;
    private String defaultValue;
    private String refDictCode;
    private String refObjectCode;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
