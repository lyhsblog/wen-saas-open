package com.wensaas.core.impl.auth.oauth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_user_oauth_binding} 表实体（混合表，tenant_id 跟随被绑用户）。
 */
@TableName("sys_user_oauth_binding")
@Getter
@Setter
public class SysUserOAuthBinding {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long userId;
    /** 渠道：dingtalk（后续 wechat / wecom）。 */
    private String provider;
    /** 渠道侧稳定标识：unionId 优先，回退 openId。 */
    private String externalId;
    private String unionId;
    private String nickname;
    private String avatarUrl;
    private LocalDateTime boundAt;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    /** 软删除时间戳；解绑置非空。 */
    private LocalDateTime deletedAt;

}
