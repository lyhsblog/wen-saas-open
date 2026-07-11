package com.wensaas.core.api.ops;

import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 运营成员的一个活跃会话/设备（个人中心「登录设备 / 会话」列表项）。
 *
 * @param sessionId    会话 ID
 * @param loginAt      登录时刻（北京时区 +08:00）
 * @param lastAccessAt 最近活跃时刻（北京时区 +08:00）
 * @param current      是否为当前请求所属会话（前端用于禁止"下线当前"）
 * @param deviceLabel  设备标签「浏览器 · 系统」（解析自 User-Agent），无法识别为「未知设备」
 * @param ip           登录时客户端 IP，可能为 null（历史会话 / 无请求上下文）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class OpsSessionItem {
    private String sessionId;
    private OffsetDateTime loginAt;
    private OffsetDateTime lastAccessAt;
    private boolean current;
    private String deviceLabel;
    private String ip;

}
