package com.wensaas.core.api.ops;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 运营成员的一条登录日志（来源 {@code sys_login_log}）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class OpsLoginLogItem {
    private Long id;
    private String account;
    private String loginMethod;
    private String action;
    private String result;
    private String failReason;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;

}
