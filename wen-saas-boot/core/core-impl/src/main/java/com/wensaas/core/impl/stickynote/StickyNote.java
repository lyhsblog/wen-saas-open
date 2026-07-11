package com.wensaas.core.impl.stickynote;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * sys_sticky_note 表实体。用户级备忘便利贴。
 *
 * <p>作用域 {@code (tenant_id, user_id)}：tenant 由 TenantLine 拦截器自动隔离，user 在业务层过滤。
 * 每个用户维护自己的一批便签，硬删。列表按 {@code created_at} 倒序（顺序稳定，不随编辑变动），
 * 无手工排序字段；{@code updated_at} 仅用于展示「更新于」。
 */
@TableName("sys_sticky_note")
@Getter
@Setter
public class StickyNote {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;
    private Long userId;

    /** 便签内容。 */
    private String content;

    /** 颜色 key（前端小色板：yellow/green/blue/pink/purple）。 */
    private String color;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
