package com.wensaas.core.impl.audit.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * {@code sys_login_log} 表实体（混合表 + 月度分区）。
 *
 * <p>分区表注意：主键 (id, created_at)；{@code created_at} 必须 INSERT 前主动赋值。
 */
@TableName(value = "sys_login_log")
@Getter
@Setter
public class SysLoginLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String requestId;
    private Long operatorId;
    private String operatorName;
    private Long operatorDeptId;
    private String account;
    /** password / sms / wechat / wecom / dingtalk */
    private String loginMethod;
    /** login / logout / session_expired */
    private String action;
    /** success / fail */
    private String result;
    /** bad_credentials / disabled / locked / oauth_failed / other */
    private String failReason;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;

}
