package com.wensaas.core.impl.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wensaas.core.impl.tenant.PgJsonbTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * sys_dict_item 表实体。混合表。
 *
 * <p>{@code ancestors} 形如 {@code /200001/200005/}，根项为 {@code /}。
 * 子树查询 {@code LIKE '/X/%'}（startsWith 语义，最大深度 5）。
 */
@TableName(value = "sys_dict_item", autoResultMap = true)
@Getter
@Setter
public class SysDictItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String groupCode;
    private String code;
    private String name;
    private Long parentId;
    private String ancestors;
    private Integer sortOrder;
    /** 0=停用 / 1=启用 */
    private Integer status;
    /** 1=SYSTEM / 2=MODULE / 3=CUSTOM */
    private Integer source;
    private String remark;
    @TableField(typeHandler = PgJsonbTypeHandler.class)
    private Map<String, Object> extra;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic(value = "null", delval = "now()")
    private LocalDateTime deletedAt;

}
