package com.wensaas.core.api.file.share;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 消费分享（密码校验 + 计数自增）成功后返回的签名下载 URL。
 *
 * @param signedDownloadUrl 5 分钟过期的下载 URL
 * @param expiresAt         URL 过期时间点
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class FileShareConsumeResult {
    private String signedDownloadUrl;
    private Instant expiresAt;

}
