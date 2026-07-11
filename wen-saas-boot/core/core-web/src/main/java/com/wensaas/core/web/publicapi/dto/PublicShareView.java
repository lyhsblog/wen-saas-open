package com.wensaas.core.web.publicapi.dto;

import com.wensaas.core.api.file.share.FileShareInfo;

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
 * 免登录分享落地页（FC-901）对外只读视图。
 *
 * <p>相比内部 {@link FileShareInfo} 收窄字段：不暴露 {@code createdBy}（用户 ID）/ {@code createdAt}
 * 等内部信息，只给落地页渲染所需的文件名 / 大小 / 是否需密码 / 过期时间 / 剩余次数。
 *
 * @param shareToken         对外短链 ID
 * @param fileName           文件名
 * @param mimeType           MIME 类型
 * @param sizeBytes          文件大小（字节）
 * @param expiresAt          过期时间；null = 永久
 * @param hasPassword        是否需要密码
 * @param maxDownloads       最大下载次数；null = 不限
 * @param remainingDownloads 剩余次数；null = 不限
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class PublicShareView {
    private String shareToken;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private LocalDateTime expiresAt;
    private boolean hasPassword;
    private Integer maxDownloads;
    private Integer remainingDownloads;

    public static PublicShareView from(FileShareInfo info) {
        return new PublicShareView(
                info.shareToken(),
                info.fileName(),
                info.mimeType(),
                info.sizeBytes(),
                info.expiresAt(),
                info.hasPassword(),
                info.maxDownloads(),
                info.remainingDownloads());
    }
}
