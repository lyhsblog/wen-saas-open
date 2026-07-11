package com.wensaas.core.impl.notification.preference;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_user_notification_preference} 实体。混合表。
 *
 * <p>按 (用户 × 类别 × 通道) 一行；缺行表示默认开启。
 */
@TableName("sys_user_notification_preference")
@Getter
@Setter
public class SysUserNotificationPreference {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long userId;
    /** NotificationCategory 枚举名 */
    private String category;
    /** Channel 枚举名 */
    private String channel;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
