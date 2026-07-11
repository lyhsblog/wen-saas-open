package com.wensaas.core.impl.viewpref;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * sys_user_view_pref 表实体。用户级列表列偏好。
 */
@TableName(value = "sys_user_view_pref", autoResultMap = true)
@Getter
@Setter
public class SysUserViewPref {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private Long userId;
    private String viewKey;

    /** 可见非固定列顺序。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> customKeys;

    /** 显式隐藏列集合。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> hiddenKeys;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
